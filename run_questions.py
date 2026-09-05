#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量科研问题自动跑题脚本（绕过前端，直连 ai-service）。

功能：
  - 读取结构化问题 JSON（如 125问题_第一组.json，每行 {index, question, description}）
  - 调用 ai-service 异步管线，自动在「人在回路」暂停点通过（resume），无需人工
  - 用 SSE 事件流检测：pipeline.pause → 通过；pipeline.done → 取报告；pipeline.error → 失败
  - 单题超时判失败并跳过（默认 10 分钟），失败不影响后续题目
  - 并发跑题（默认 2 题，项目 pipelinePool 为缓存线程池，支持多并发）
  - 结果按前端 MD 模板落盘到输出目录；末尾写汇总

与 start.py 的区别：本脚本【不启动/停止服务】。请先用 start.py 把服务启好（--prod），
再单独运行本脚本。

用法（在项目根目录）：
    python run_questions.py --input 125问题_第一组.json --out 第一组问题结果
    python run_questions.py --input xx.json --out out --workers 2 --timeout 10 --base http://localhost:8081
"""

import argparse
import json
import queue
import re
import sys
import threading
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path


# ==================== 默认参数 ====================

DEFAULT_BASE = "http://localhost:8081"   # ai-service 地址
DEFAULT_WORKERS = 2                      # 同时最多跑几题
DEFAULT_TIMEOUT_MIN = 10                 # 单题超时（分钟）
SSE_READ_TIMEOUT = 180                   # SSE 单次读取超时（秒），覆盖最长 LLM 调用间隔
SUMMARY_NAME = "_汇总.md"


# ==================== HTTP 工具（仅标准库） ====================

def http_get_json(url, timeout=15):
    """GET 并返回 JSON。"""
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        text = resp.read().decode("utf-8")
    return json.loads(text) if text.strip() else {}


def http_post_json(url, payload=None, timeout=60):
    """POST JSON；payload 为 None 时发送空 JSON 对象。返回解析后的 dict。"""
    body = json.dumps(payload if payload is not None else {}).encode("utf-8")
    req = urllib.request.Request(
        url, data=body, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        text = resp.read().decode("utf-8")
    return json.loads(text) if text.strip() else {}


def check_service(base):
    """预检：确认 ai-service 可访问。失败则抛出带指引的异常。"""
    try:
        http_get_json(f"{base}/pipeline/runs")
    except Exception as exc:
        raise SystemExit(
            f"[error] 无法连接 ai-service（{base}）。请先运行 python start.py --prod 启动服务后，"
            f"再运行本脚本。原始错误：{exc}") from exc


def start_run(base, question):
    """启动管线，返回 runId。"""
    result = http_post_json(f"{base}/pipeline/run", {"question": question})
    run_id = result.get("runId")
    if not run_id:
        raise RuntimeError(f"启动管线未返回 runId：{result}")
    return run_id


def fetch_failure_detail(base, run_id):
    """取管线 trace 中失败阶段的 errorMessage（内层真正原因，外层只是“阶段 XX 执行失败”）。"""
    try:
        data = http_get_json(f"{base}/pipeline/{run_id}/trace")
        if isinstance(data, list):
            for rec in data:
                if isinstance(rec, dict) and rec.get("status") == "FAILED" and rec.get("errorMessage"):
                    return rec["errorMessage"]
    except Exception:
        pass
    return None


def resume_run(base, run_id):
    """自动通过人在回路暂停点。

    注意：PipelineEngine.resume 内部用 Map.of("comment", …) 发布事件，Map.of 不允许 null；
    因此这里必须带非空 reviewComment（空 body 会触发引擎 NPE，导致管线卡死）。
    """
    try:
        http_post_json(f"{base}/pipeline/{run_id}/resume",
                       {"reviewComment": "自动通过", "revisedHypotheses": []}, timeout=30)
    except Exception as exc:  # resume 失败不致命，继续等待后续事件
        print(f"      [warn] resume 调用异常：{exc}")


def read_sse(base, run_id, out_queue):
    """后台线程：订阅 SSE 事件流，把 (event, data) 放入队列。异常以 __error__ 结束。"""
    try:
        url = f"{base}/pipeline/{run_id}/stream"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=SSE_READ_TIMEOUT) as resp:
            event_name = None
            data_lines = []
            for raw in resp:
                line = raw.decode("utf-8", errors="replace").rstrip("\r\n")
                if line == "":
                    if event_name:
                        out_queue.put((event_name, "\n".join(data_lines)))
                    event_name = None
                    data_lines = []
                elif line.startswith("event:"):
                    event_name = line[len("event:"):].strip()
                elif line.startswith("data:"):
                    data_lines.append(line[len("data:"):].strip())
            # 流结束（pipeline.done/error 后 complete）时 flush 最后一个事件
            if event_name:
                out_queue.put((event_name, "\n".join(data_lines)))
    except Exception as exc:  # 读超时 / 连接断开等
        out_queue.put(("__error__", str(exc)))


# ==================== 单题运行 ====================

def run_one(base, question, timeout_sec, index=None):
    """
    跑一道题。返回 (ok, payload)：
      ok=True  → payload 为报告 dict（10 字段）
      ok=False → payload 为失败原因字符串
    """
    run_id = None
    try:
        run_id = start_run(base, question)
        evt_queue = queue.Queue()
        sse_thread = threading.Thread(target=read_sse, args=(base, run_id, evt_queue), daemon=True)
        sse_thread.start()

        started = time.time()
        while True:
            if time.time() - started > timeout_sec:
                return False, f"超时（>{timeout_sec}s）未出报告"
            try:
                event, data = evt_queue.get(timeout=1)
            except queue.Empty:
                continue

            if event == "pipeline.pause":
                resume_run(base, run_id)
            elif event == "pipeline.done":
                report = _parse_event(data).get("report")
                if not report:
                    return False, "pipeline.done 事件缺少 report"
                return True, report
            elif event == "pipeline.error":
                msg = str(_parse_event(data).get("message", "管线错误"))
                rid = _parse_event(data).get("runId") or run_id
                detail = fetch_failure_detail(base, rid)
                return False, (detail if detail else msg)
            elif event == "__error__":
                # SSE 读取层异常：若非超时则报错，否则继续等（由外层超时兜底）
                if "timed out" in str(data).lower() or "timeout" in str(data).lower():
                    continue
                return False, f"SSE 连接异常：{data}"
    except Exception as exc:
        return False, f"运行异常：{exc}"


def _parse_event(data_text):
    try:
        return json.loads(data_text)
    except Exception:
        return {}


# ==================== MD 渲染（复用前端 exportMarkdown 模板） ====================

def _md_list(items):
    return "\n".join(f"- {item}" for item in items) if items else "- 暂无内容"


def _text(value, fallback="待生成"):
    return value if value else fallback


def render_markdown(report, question):
    """按前端 ResultView.exportMarkdown 的 10 字段结构渲染 MD，顶部带题目（不带 description）。"""
    datasets = report.get("datasets") or {}
    experiments = report.get("experiments") or {}
    title = _text(report.get("paperTitle"), "科学假设与研究计划")
    lines = [
        f"# {title}",
        "",
        f"> 题目：{question}",
        "",
        "## 摘要（Paper Abstract）", "", _text(report.get("paperAbstract")),
        "## 1. 待研究问题（Problem Statement）", "", _text(report.get("problemStatement")),
        "## 2. 解决思路（Rationale）", "", _text(report.get("rationale")),
        "## 3. 必要的技术手段（Technical Details）", "", _md_list(report.get("technicalDetails")),
        "## 4. 数据集（Datasets）", "", "### Source", _md_list(datasets.get("source")),
        "### Target", _md_list(datasets.get("target")),
        "## 5. 论文标题（Paper Title）", "", _text(report.get("paperTitle")),
        "## 6. 论文摘要（Paper Abstract）", "", _text(report.get("paperAbstract")),
        "## 7. 方法论（Methods）", "", _md_list(report.get("methods")),
        "## 8. 实验设计（Experiments）", "", "### Baselines", _md_list(experiments.get("baselines")),
        "### Metrics", _md_list(experiments.get("metrics")),
        "## 9. 预期结果（Results）", "", _text(report.get("results")),
        "## 10. 参考论文（References）", "", _md_list(report.get("references")),
    ]
    # 去掉多余连续空行（同前端 filter 逻辑）
    filtered = [line for i, line in enumerate(lines)
                if line or i == 0 or (i > 0 and lines[i - 1] != "")]
    return "\n".join(filtered) + "\n"


def safe_name(question, limit=30):
    """生成安全的文件名片段：保留字母/数字/中文，其余转 _，限制长度。"""
    s = re.sub(r"[^\w\s-]", "_", question or "")
    s = re.sub(r"\s+", "_", s).strip("_")
    s = re.sub(r"_+", "_", s)
    return s[:limit].strip("_") or "question"


# ==================== 主流程 ====================

def load_questions(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    if isinstance(data, dict):
        data = data.get("questions", data.get("items", []))
    if not isinstance(data, list):
        raise ValueError(f"{path} 应为 JSON 数组或含 questions/items 的对象")
    items = []
    for i, row in enumerate(data, start=1):
        if not isinstance(row, dict):
            continue
        question = str(row.get("question", "")).strip()
        description = str(row.get("description", "")).strip()
        if not question:
            continue
        # 输入 = question + 换行 + description（合并，B）
        full = f"{question}\n{description}" if description else question
        items.append({
            "index": row.get("index", i),
            "question": question,
            "input": full,
        })
    return items


def parse_failed_indices(summary_md):
    """解析上次 _汇总.md，返回失败题的 index 集合（❌ 行）。"""
    failed = set()
    for line in summary_md.splitlines():
        m = re.match(r"^\- ❌ 第 (\d+) 题", line.strip())
        if m:
            failed.add(int(m.group(1)))
    return failed


def parse_summary_state(summary_md):
    """解析 _汇总.md 为 {index: {"ok": bool, "question": str}}，用于合并全量最新状态。"""
    state = {}
    for line in summary_md.splitlines():
        m = re.match(r"^\- (✅|❌) 第 (\d+) 题：(.+)$", line.strip())
        if m:
            ok = m.group(1) == "✅"
            state[int(m.group(2))] = {"ok": ok, "question": m.group(3).strip()}
    return state


def latest_summary(out_dir: Path):
    """返回输出目录里【最新】的汇总文件：优先带时间戳的 _汇总_*.md（mtime 最新），回退 _汇总.md。"""
    candidates = []
    if out_dir.exists():
        for p in out_dir.glob(f"{SUMMARY_NAME.rsplit('.', 1)[0]}_*.md"):
            candidates.append(p)
        plain = out_dir / SUMMARY_NAME
        if plain.exists():
            candidates.append(plain)
    if not candidates:
        return None
    return max(candidates, key=lambda p: p.stat().st_mtime)


def timestamp():
    """当前时间戳（用于汇总文件名），如 20260905_1230。"""
    return time.strftime("%Y%m%d_%H%M")


def main():
    parser = argparse.ArgumentParser(description="批量科研问题自动跑题（直连 ai-service，不启动服务）")
    parser.add_argument("--input", default="125问题_第一组.json", help="问题 JSON 路径")
    parser.add_argument("--out", default="第一组问题结果", help="输出目录（MD + 汇总）")
    parser.add_argument("--workers", type=int, default=DEFAULT_WORKERS, help="并发题数（默认 2）")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT_MIN,
                        help="单题超时（分钟，默认 10）")
    parser.add_argument("--base", default=DEFAULT_BASE, help="ai-service 地址（默认 8081）")
    parser.add_argument("--limit", type=int, default=None, help="只跑前 N 题（用于试跑）")
    parser.add_argument("--retry-failed", type=str, default=None,
                        help="只重跑上次汇总(_汇总.md)中失败的题；值为上次输出目录路径")
    args = parser.parse_args()

    check_service(args.base)

    items = load_questions(args.input)

    # —— 只重跑失败的：优先读【最新】带时间戳汇总（反映最近一次运行），否则读 _汇总.md ——
    retry_failed = set()
    if args.retry_failed:
        summary_path = latest_summary(Path(args.retry_failed))
        if not summary_path:
            print(f"[error] 未找到上次汇总（{Path(args.retry_failed)} 下无 _汇总.md / _汇总_*.md），无法识别失败题")
            sys.exit(1)
        retry_failed = parse_failed_indices(summary_path.read_text(encoding="utf-8"))
        if not retry_failed:
            print("[info] 上次汇总无失败题，无需重跑")
            sys.exit(0)
        items = [it for it in items if it["index"] in retry_failed]
        print(f"[retry] 读取 {summary_path.name}：上次失败 {len(retry_failed)} 题，本次只重跑这些")

    if args.limit:
        items = items[:args.limit]
    if not items:
        print("[error] 未读到任何有效问题，请检查 JSON 结构")
        sys.exit(1)

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    timeout_sec = args.timeout * 60

    print(f"[run] 共 {len(items)} 题 | 并发 {args.workers} | 单题超时 {args.timeout} 分钟")
    print(f"[run] ai-service: {args.base} | 输出: {out_dir}")

    results = []
    with ThreadPoolExecutor(max_workers=max(1, args.workers)) as executor:
        futures = {}
        for item in items:
            futures[executor.submit(
                run_one, args.base, item["input"], timeout_sec, item["index"])] = item
        for future in as_completed(futures):
            item = futures[future]
            ok, payload = future.result()
            results.append({
                "index": item["index"],
                "question": item["question"],
                "ok": ok,
                "detail": payload,
            })
            status = "✅ 成功" if ok else f"❌ 失败（{payload}）"
            print(f"  [{item['index']}] {status}")

    # 按 index 排序
    results.sort(key=lambda r: r["index"])

    # 写 MD + 汇总（_汇总.md 始终为全量最新状态；_汇总_<时间戳>.md 为本次重跑历史）
    ok_count = 0
    # 重跑模式下：先读旧 _汇总.md 里「非本次重跑」的成功题，合并为全量最新
    merged = {}  # index -> {ok, question}
    if args.retry_failed:
        old_summary = Path(args.out) / SUMMARY_NAME
        if old_summary.exists():
            merged = parse_summary_state(old_summary.read_text(encoding="utf-8"))
        # 本次重跑的题覆盖旧状态
        for r in results:
            merged[r["index"]] = {"ok": r["ok"], "question": r["question"]}
    else:
        for r in results:
            merged[r["index"]] = {"ok": r["ok"], "question": r["question"]}

    # 写 MD（本次重跑成功的题）+ 更新全量状态
    for r in results:
        if r["ok"]:
            ok_count += 1
            fname = f"{r['index']}_{safe_name(r['question'])}.md"
            (out_dir / fname).write_text(
                render_markdown(r["detail"], r["question"]), encoding="utf-8")

    # 全量最新状态 -> _汇总.md
    full_lines = ["# 跑题结果汇总（全量最新状态）", "",
                  f"- 输入：{args.input}", f"- 并发：{args.workers}", f"- 单题超时：{args.timeout} 分钟",
                  f"- ai-service：{args.base}", f"- 最近更新：{time.strftime('%Y-%m-%d %H:%M')}", ""]
    for idx in sorted(merged):
        st = merged[idx]
        line = f"- ✅ 第 {idx} 题：{st['question']}" if st["ok"] else f"- ❌ 第 {idx} 题：{st['question']} —— （待重跑/失败）"
        full_lines.append(line)
    full_ok = sum(1 for st in merged.values() if st["ok"])
    full_lines += ["", f"**成功 {full_ok}/{len(merged)}，失败 {len(merged) - full_ok}**"]
    (out_dir / SUMMARY_NAME).write_text("\n".join(full_lines) + "\n", encoding="utf-8")

    # 本次重跑历史 -> _汇总_<时间戳>.md
    hist_lines = ["# 跑题结果汇总（本次重跑）", "",
                  f"- 输入：{args.input}", f"- 时间：{time.strftime('%Y-%m-%d %H:%M')}", ""]
    for r in results:
        if r["ok"]:
            hist_lines.append(f"- ✅ 第 {r['index']} 题：{r['question']}")
        else:
            hist_lines.append(f"- ❌ 第 {r['index']} 题：{r['question']} —— {r['detail']}")
    hist_lines += ["", f"**本次成功 {ok_count}/{len(results)}，失败 {len(results) - ok_count}**"]
    hist_name = f"{SUMMARY_NAME.rsplit('.', 1)[0]}_{timestamp()}.md"
    (out_dir / hist_name).write_text("\n".join(hist_lines) + "\n", encoding="utf-8")

    print(f"\n[run] 完成：本次成功 {ok_count}/{len(results)}，失败 {len(results) - ok_count}")
    print(f"[run] 全量汇总：{out_dir / SUMMARY_NAME}")
    print(f"[run] 本次历史：{out_dir / hist_name}")
    print(f"[run] MD：{out_dir}")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[ctrl+c] 已中断")
        sys.exit(130)
