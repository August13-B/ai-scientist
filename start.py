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
        key = key.strip()
        # 去掉行内注释（# 起），再去掉可能包裹的引号与首尾空白，避免值被注释污染（如 VECTOR_DB=chroma # xxx）
        value = value.split("#", 1)[0].strip().strip('"').strip("'")
        if key:
            env[key] = value
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


def jar_is_stale(jar: Path, module_dir: Path) -> bool:
    """判断 jar 是否比源码/构建文件旧（避免 Windows 侧跑过期构建）。

    源码或 pom.xml 的修改时间晚于 jar 构建时间时视为过期，触发重新打包。
    """
    if not jar.exists():
        return True
    jar_mtime = jar.stat().st_mtime
    src_dir = module_dir / "src"
    candidates = [module_dir / "pom.xml"]
    if src_dir.exists():
        candidates += [
            p for p in src_dir.rglob("*")
            if p.is_file() and p.suffix in (".java", ".yml", ".yaml", ".properties", ".xml")
        ]
    return any(p.exists() and p.stat().st_mtime > jar_mtime for p in candidates)


def find_mvn(module_dir: Path):
    """按优先级定位 Maven：模块内 Maven Wrapper → 系统 PATH → 常见安装目录/环境变量。"""
    # 1) 模块内 Maven Wrapper（mvnw.cmd / mvnw）
    for wrapper in ("mvnw.cmd", "mvnw"):
        candidate = module_dir / wrapper
        if candidate.exists():
            return str(candidate)
    # 2) 系统 PATH
    mvn = shutil.which("mvn") or (shutil.which("mvn.cmd") if os.name == "nt" else None)
    if mvn:
        return mvn
    # 3) 项目内自带 Maven（.tools/apache-maven-*/bin/mvn.cmd）—— 无需全局安装
    tools_dir = ROOT / ".tools"
    if tools_dir.exists():
        for pattern in ("apache-maven-*/bin/mvn.cmd", "apache-maven-*/bin/mvn"):
            matches = sorted(tools_dir.glob(pattern))
            if matches:
                return str(matches[0])
    # 4) 常见安装目录 / 环境变量（Windows）
    import glob as _glob
    candidates = [
        os.environ.get("M2_HOME"), os.environ.get("MAVEN_HOME"),
        r"C:\apache-maven*\bin\mvn.cmd", r"C:\maven\bin\mvn.cmd",
        r"C:\Program Files\apache-maven*\bin\mvn.cmd",
        r"C:\Users\*\scoop\apps\maven\current\bin\mvn.cmd",
    ]
    for cand in candidates:
        if not cand:
            continue
        if "*" in cand:
            matches = _glob.glob(cand)
            if matches:
                return matches[0]
        elif Path(cand).exists():
            return cand
    return None


def ensure_jar(module_dir: Path, jar: Path, name: str) -> bool:
    """确保 jar 可用：新鲜则直接可启动；过期则用 Maven 重建。返回是否可启动。"""
    if not jar_is_stale(jar, module_dir):
        return True
    mvn = find_mvn(module_dir)
    if mvn is None:
        print(f"[error] {name} 源码比 jar 新，需要重新打包，但未找到 Maven。")
        print("       请先安装 Maven（PowerShell: winget install Apache.Maven），或")
        print("       用 IDE 对该模块执行 mvn package，再重新运行本脚本。")
        return False
    print(f"[build] {name} 重新打包（源码比 jar 新，避免跑过期构建）…")
    # -Dmaven.test.skip=true：跳过测试编译与执行（构建可运行 jar 无需测试；需跑测试时单独 mvn test）
    # clean：清掉旧 target，确保打包的是当前源码（避免增量残留旧 class）
    result = subprocess.run([mvn, "clean", "package", "-Dmaven.test.skip=true"],
                            cwd=module_dir, env=os.environ)
    if result.returncode != 0:
        print(f"[error] {name} 打包失败（Maven 返回码 {result.returncode}），终止启动以免跑过期 jar。")
        print("       若报“另一个程序正在使用此文件/进程无法访问”，说明有旧服务仍占用 jar：")
        print("       请先 python start.py --stop 停止全部服务，再重新运行本脚本。")
        return False
    if not jar.exists():
        print(f"[error] {name} 打包完成但未生成 jar，请检查 Maven 日志")
        return False
    return True


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
    # Windows 下 JVM 进程终止后仍需短暂释放文件锁，等待后再继续，避免重建 jar 被占用
    time.sleep(2)
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
    if not ensure_jar(ai_dir, jar, "ai-service"):
        return
    env = dict(env)
    env.setdefault("AI_SERVICE_PORT", "8081")
    start_service("ai-service", ai_dir, [java_cmd(), "-jar", str(jar)], env=env)


def start_backend(env):
    start_mysql()
    backend_dir = ROOT / "backend"
    jar = backend_dir / "target" / "backend-0.1.0.jar"
    if not ensure_jar(backend_dir, jar, "backend"):
        return
    env = dict(env)
    env.setdefault("BACKEND_PORT", "8080")
    start_service("backend", backend_dir, [java_cmd(), "-jar", str(jar)], env=env)


def docker_available() -> bool:
    """检查 docker CLI 存在且 daemon 可运行（Docker Desktop 是否启动）。"""
    docker = shutil.which("docker")
    if not docker:
        return False
    try:
        result = subprocess.run([docker, "info"],
                                capture_output=True, timeout=5, cwd=ROOT)
        return result.returncode == 0
    except Exception:
        return False


def docker_missing_hint(kind: str) -> None:
    print(f"[warn] 未检测到可用的 Docker，无法自动启动 {kind}。")
    print(f"        请先启动 Docker Desktop，或手动执行：docker compose up -d {kind} "
          f"（在本项目根目录）")


def start_mysql():
    if not docker_available():
        docker_missing_hint("MySQL（backend 需要）")
        return
    print("[docker] 启动 MySQL…")
    subprocess.run(["docker", "compose", "up", "-d", "mysql"], cwd=ROOT)


def start_chroma():
    if not docker_available():
        docker_missing_hint("Chroma（正式模式 RAG 需要）")
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
