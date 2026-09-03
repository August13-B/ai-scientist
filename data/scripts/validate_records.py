#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
灌库输入 JSONL 契约校验（四库字段标准执行版）。

字段标准权威文档：docs/rag-field-standard.md（§2 输入契约 / §6 source_id 规则）。
用法：
    python scripts/validate_records.py --library papers  --input data/processed/papers.jsonl
    python scripts/validate_records.py --library methods --input data/processed/methods.jsonl
    python scripts/validate_records.py --input data/processed            # 按文件名识别四库

exit code：0 = 全部通过；1 = 存在错误（逐条打印 行号 + 原因）。
"""

import argparse
import json
import sys
from pathlib import Path

# ==================== 字段标准（与 docs/rag-field-standard.md §2 保持一致） ====================

# 库名 -> （必填字段, 来源标识字段组, title 等价源字段）
# 来源标识：至少命中一组中的一种
CONTRACT = {
    "papers": {
        "required": ("title",),
        "source_groups": (("doi", "pmid", "url"),),
        "title_source": ("title",),
        "display": "论文库",
    },
    "methods": {
        "required": ("method_name", "scenario"),
        "source_groups": (("source_doi", "source_pmid", "source_url"),),
        "title_source": ("method_name",),
        "display": "方法库",
    },
    "datasets": {
        "required": ("name",),
        "source_groups": (("url",),),
        "title_source": ("name",),
        "display": "数据集库",
    },
    "evidence": {
        "required": ("subject", "predicate", "object"),
        "source_groups": (("source_pmid", "source_doi", "source_url"),),
        "title_source": ("subject", "predicate", "object"),
        "display": "证据库",
    },
}

# 文件名前缀 -> 库名（--input 目录模式时按文件名自动识别）
FILENAME_PREFIX = {
    "papers": "papers",
    "methods": "methods",
    "datasets": "datasets",
    "evidence": "evidence",
}


def _library_of_file(path: Path) -> str | None:
    name = path.name.lower()
    for library, prefix in FILENAME_PREFIX.items():
        if name.startswith(prefix):
            return library
    return None


def validate_records(path: Path, library: str) -> tuple[int, list[str]]:
    """校验单个 JSONL 文件，返回 (记录数, 错误消息列表)。"""
    contract = CONTRACT[library]
    label = contract["display"]
    if not path.exists():
        return 0, [f"文件不存在: {path}"]
    if path.suffix.lower() != ".jsonl":
        return 0, [f"仅支持 .jsonl: {path}（当前 {path.suffix or '无后缀'}）"]

    errors: list[str] = []
    count = 0
    with path.open("r", encoding="utf-8") as handle:
        for line_no, line in enumerate(handle, start=1):
            line = line.strip()
            if not line:
                continue
            try:
                record = json.loads(line)
            except json.JSONDecodeError as exc:
                errors.append(f"{label} {path.name}:{line_no} JSON 解析失败: {exc}")
                continue
            if not isinstance(record, dict):
                errors.append(f"{label} {path.name}:{line_no} 记录必须是 JSON 对象")
                continue
            count += 1

            # 1) 必填字段（title 等价源字段视为必填：缺失将导致检索侧 title 为空）
            for field in contract["required"] + contract["title_source"]:
                value = record.get(field)
                if value is None or (isinstance(value, str) and not value.strip()):
                    errors.append(
                        f"{label} {path.name}:{line_no} 缺少必填字段或为空: {field}")
                    continue
                if isinstance(value, (list, dict)) and len(value) == 0:
                    errors.append(
                        f"{label} {path.name}:{line_no} 必填字段为空集合: {field}")

            # 2) 来源标识：至少命中一组（如 papers 的 doi/pmid/url 三选一）
            for group in contract["source_groups"]:
                if not any(_non_blank(record.get(key)) for key in group):
                    errors.append(
                        f"{label} {path.name}:{line_no} 缺少来源标识（需含 "
                        + "/".join(group) + " 至少一种）")

    return count, errors


def _non_blank(value) -> bool:
    if value is None:
        return False
    if isinstance(value, str):
        return bool(value.strip())
    return True


def _auto_discover(input_dir: Path) -> list[tuple[Path, str]]:
    jobs = []
    for path in sorted(input_dir.glob("*.jsonl")):
        library = _library_of_file(path)
        if library is None:
            print(f"⚠️  跳过无法识别库的文件: {path.name}（需以 "
                  f"{'/'.join(sorted(FILENAME_PREFIX))} 之一开头命名）")
            continue
        jobs.append((path, library))
    return jobs


def main() -> int:
    parser = argparse.ArgumentParser(description="四库灌库 JSONL 契约校验（标准见 docs/rag-field-standard.md）")
    parser.add_argument("--input", required=True,
                        help="JSONL 文件路径，或包含 papers/methods/datasets/evidence 四份 jsonl 的目录")
    parser.add_argument("--library", choices=sorted(CONTRACT),
                        help="库名（--input 为文件时必填；目录模式按文件名自动识别）")
    args = parser.parse_args()

    input_path = Path(args.input)
    if input_path.is_dir():
        jobs = _auto_discover(input_path)
        if not jobs:
            print("❌ 目录内未发现任何 papers/methods/datasets/evidence JSONL")
            return 1
    else:
        if not args.library:
            parser.error("--input 为单个文件时必须指定 --library")
        jobs = [(input_path, args.library)]

    all_errors: list[str] = []
    total_records = 0
    for path, library in jobs:
        count, errors = validate_records(path, library)
        total_records += count
        status = "✅" if not errors else "❌"
        print(f"{status} {CONTRACT[library]['display']} {path.name}: {count} 条记录"
              f"{'' if not errors else f'，{len(errors)} 处错误'}")
        all_errors.extend(errors)

    for error in all_errors:
        print(f"   ✗ {error}")

    if all_errors:
        print(f"\n❌ 校验未通过：共 {len(all_errors)} 处错误（{total_records} 条记录）。"
              f"修复后重试，详见 docs/rag-field-standard.md")
        return 1
    print(f"\n✅ 校验通过：{total_records} 条记录全部符合四库字段标准"
          f"（docs/rag-field-standard.md）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
