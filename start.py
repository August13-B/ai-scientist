#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI Scientist 一键启动脚本（跨平台，Windows PowerShell / WSL / Linux 均可）。

用法：
    python start.py                  交互主菜单（可启动全部/各服务/停止）
    python start.py --only all       免交互启动全部（默认调试模式）
    python start.py --only ai        只启动 ai-service
    python start.py --prod           正式模式（RAG_MOCK_SAMPLES=false，自动起 chroma + mysql）
    python start.py --stop           停止全部已启动的服务

功能：
  - 读取仓库根目录 .env（ALIYUN_BAILIAN_API_KEY / RAG_MOCK_SAMPLES / 端口等）
  - 自动处理中间件：mysql（docker compose）、chroma（正式模式才起）
  - ai-service / backend：自动 mvn package 后 java -jar；frontend：npm install + npm run dev
  - 各服务日志重定向到 logs/*.log，Ctrl+C 或 --stop 一键结束全部
"""

import argparse
import os
import shutil
import signal
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LOGS = ROOT / "logs"
PROCESSES = []


# ==================== .env 读取与工具 ====================

def load_env(path: Path) -> dict:
    """极简 .env 解析（不依赖第三方库）。"""
    env = {}
    if not path.exists():
        print(f"[warn] 未找到 {path}，请先配置 .env（至少填 ALIYUN_BAILIAN_API_KEY）")
        return env
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        env[key.strip()] = value.strip().strip('"').strip("'")
    return env


def java_cmd() -> str:
    """优先用 JAVA_HOME，否则 PATH 里的 java。"""
    j = os.environ.get("JAVA_HOME")
    if j:
        candidate = Path(j) / "bin" / ("java.exe" if os.name == "nt" else "java")
        if candidate.exists():
            return str(candidate)
    found = shutil.which("java")
    if not found:
        print("[error] 未检测到 java，请安装 JDK 17 并配置 PATH/JAVA_HOME")
        sys.exit(1)
    return found


def npm_cmd():
    return shutil.which("npm") or (shutil.which("npm.cmd") if os.name == "nt" else None)


def pid_alive(pid: str) -> bool:
    if not pid.isdigit():
        return False
    try:
        os.kill(int(pid), 0)
        return True
    except OSError:
        return False


# ==================== 进程管理 ====================

def start_service(name, cwd, args, env=None):
    pid_file = ROOT / f".run_{name}.pid"
    if pid_file.exists():
        pid = pid_file.read_text().strip()
        if pid and pid_alive(pid):
            print(f"[skip] {name} 已在运行（pid {pid}）")
            return
    LOGS.mkdir(exist_ok=True)
    log_path = LOGS / f"{name}.log"
    with log_path.open("w", encoding="utf-8") as log_file:
        print(f"[start] {name} -> {' '.join(args)}")
        env_full = os.environ.copy()
        if env:
            env_full.update(env)
        proc = subprocess.Popen(
            args, cwd=cwd, stdout=log_file, stderr=subprocess.STDOUT,
            env=env_full, shell=False,
            creationflags=getattr(subprocess, "CREATE_NEW_PROCESS_GROUP", 0),
        )
    PROCESSES.append(proc)
    pid_file.write_text(str(proc.pid))
    print(f"  [log] {log_path}")


def stop_all():
    print("[stop] 停止所有已启动服务…")
    for pid_file in ROOT.glob(".run_*.pid"):
        pid = pid_file.read_text().strip()
        if pid.isdigit():
            try:
                os.kill(int(pid), signal.SIGTERM)
                print(f"  已终止 pid {pid}")
            except OSError:
                pass
        pid_file.unlink(missing_ok=True)
    print("[stop] 完成")


# ==================== 启动逻辑 ====================

def prepare_environment(env_file: Path, prod: bool) -> dict:
    env = load_env(env_file)
    if prod:
        env["RAG_MOCK_SAMPLES"] = "false"
        env.setdefault("VECTOR_DB", "chroma")
    else:
        env["RAG_MOCK_SAMPLES"] = "true"
    return env


def start_ai(env):
    ai_dir = ROOT / "ai-service"
    jar = ai_dir / "target" / "ai-service-0.1.0.jar"
    if not jar.exists():
        print("[build] ai-service 未打包，先 mvn package…")
        mvn = shutil.which("mvn")
        if mvn:
            subprocess.run([mvn, "package", "-DskipTests"], cwd=ai_dir, env=os.environ)
            if not jar.exists():
                print("[error] ai-service 打包失败，请检查 Maven")
                return
        else:
            print("[error] ai-service 无 jar 且未检测到 mvn，请先 mvn package")
            return
    env = dict(env)
    env.setdefault("AI_SERVICE_PORT", "8081")
    start_service("ai-service", ai_dir, [java_cmd(), "-jar", str(jar)], env=env)


def start_backend(env):
    start_mysql()
    backend_dir = ROOT / "backend"
    jar = backend_dir / "target" / "backend-0.1.0.jar"
    if not jar.exists():
        mvn = shutil.which("mvn")
        if mvn:
            print("[build] backend 未打包，mvn package…")
            subprocess.run([mvn, "package", "-DskipTests"], cwd=backend_dir, env=os.environ)
        if not jar.exists():
            print("[warn] backend 无 jar 且无法构建，跳过 backend")
            return
    env = dict(env)
    env.setdefault("BACKEND_PORT", "8080")
    start_service("backend", backend_dir, [java_cmd(), "-jar", str(jar)], env=env)


def start_mysql():
    docker = shutil.which("docker")
    if not docker:
        print("[warn] 未检测到 docker，无法自动启动 MySQL（backend 需 MySQL，请手动起）")
        return
    print("[docker] 启动 MySQL…")
    subprocess.run(["docker", "compose", "up", "-d", "mysql"], cwd=ROOT)


def start_chroma():
    docker = shutil.which("docker")
    if not docker:
        print("[warn] 未检测到 docker，无法自动启动 Chroma（正式模式需要）")
        return
    print("[docker] 启动 Chroma…")
    subprocess.run(["docker", "compose", "up", "-d", "chroma"], cwd=ROOT)


def start_frontend():
    fe_dir = ROOT / "frontend"
    node = shutil.which("node")
    npm = npm_cmd()
    if not (node and npm):
        print("[warn] 未检测到 node/npm，无法启动前端（请安装 Node.js）")
        return
    if not (fe_dir / "node_modules").exists():
        print("[npm] frontend 首次运行，安装依赖…")
        subprocess.run([npm, "install"], cwd=fe_dir)
    env = dict(os.environ)
    env.setdefault("VITE_API_BASE", "http://localhost:8080")
    start_service("frontend", fe_dir, [npm, "run", "dev"], env=env)


def run_target(target, env, prod):
    if target in ("all", "backend"):
        start_mysql()
    if prod and target in ("all", "ai"):
        start_chroma()
    if target in ("all", "ai"):
        start_ai(env)
    if target in ("all", "backend"):
        start_backend(env)
    if target in ("all", "frontend"):
        start_frontend()
    try:
        while True:
            time.sleep(60)
    except KeyboardInterrupt:
        print("\n[ctrl+c] 停止…")
        stop_all()


def mode_banner(prod: bool) -> str:
    """返回当前模式横幅文案。"""
    if prod:
        return (
            "🧊 当前模式：生产模式（正式）\n"
            "   · RAG_MOCK_SAMPLES=false（真实四库）\n"
            "   · 需 Chroma（已导入四库向量）+ MySQL\n"
            "   · 引用/数据集防幻觉严格生效\n"
        )
    return (
        "🟢 当前模式：调试模式（本地联调，默认）\n"
        "   · RAG_MOCK_SAMPLES=true（内置 mock 样例论文）\n"
        "   · 无需 Chroma / RAG 数据即可跑通 ①-⑧\n"
        "   · 引用严格校验放宽（测试环境不需要防幻觉）\n"
    )


def main():
    parser = argparse.ArgumentParser(description="AI Scientist 一键启动")
    parser.add_argument("--only", nargs="?", default=None, const="all",
                        choices=["all", "ai", "backend", "frontend"],
                        help="直接启动指定服务（默认交互菜单）")
    parser.add_argument("--prod", action="store_true",
                        help="正式模式（RAG_MOCK_SAMPLES=false，起 chroma + mysql）")
    parser.add_argument("--stop", action="store_true", help="停止全部已启动服务")
    args = parser.parse_args()

    if args.stop:
        stop_all()
        return

    env = prepare_environment(ROOT / ".env", args.prod)

    if args.only:
        run_target(args.only, env, args.prod)
        return

    # 免交互模式：启动前打印模式说明
    if args.only:
        print(mode_banner(args.prod))
        run_target(args.only, env, args.prod)
        return

    # 交互主菜单：支持切换调试/生产模式
    prod = args.prod
    while True:
        print(mode_banner(prod))
        print("  [切换] 用 python start.py --prod 进入生产；或下面选 6 切换\n")
        print("=== 一键启动菜单 ===")
        print("  1. 启动全部（ai-service + backend + frontend + 中间件）")
        print("  2. 仅 ai-service")
        print("  3. 仅 backend（含 MySQL）")
        print("  4. 仅 frontend")
        print("  5. 停止全部")
        print("  6. 切换 调试/生产 模式")
        print("  7. 退出")
        choice = input("请选择 (1-7): ").strip()
        env = prepare_environment(ROOT / ".env", prod)
        if choice == "1":
            run_target("all", env, prod)
        elif choice == "2":
            run_target("ai", env, prod)
        elif choice == "3":
            run_target("backend", env, prod)
        elif choice == "4":
            run_target("frontend", env, prod)
        elif choice == "5":
            stop_all()
        elif choice == "6":
            prod = not prod
            print(f"\n⏺ 已切换到 {'生产' if prod else '调试'} 模式\n")
        elif choice == "7":
            print("再见")
            return
        else:
            print("无效选择\n")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[ctrl+c] 停止…")
        stop_all()
