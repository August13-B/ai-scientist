# 部署与启动指南（Deployment Guide）

> 更新时间：2026-09-03
> 覆盖：一键启动脚本、手动启动、**调试 vs 正式模式**、环境变量、常见问题。

## 0. 两种运行模式总览

系统支持「调试模式」与「正式模式」，由 `.env` 的 `RAG_MOCK_SAMPLES` 控制（一键脚本自动设置）：

| 维度 | 调试模式（默认） | 正式模式（`--prod`） |
|---|---|---|
| `RAG_MOCK_SAMPLES` | `true` | `false` |
| RAG 数据源 | 内置 mock 样例论文（4 篇，DOI 真实可核验） | 真实四库（Chroma + 灌库数据） |
| 中间件 | 无需 Chroma；backend 用 MySQL | 需 Chroma + MySQL（docker compose） |
| 用途 | 本地开发/联调，快速跑通 ①-⑧ | 正式展示/评测，真实数据 |

> **调试模式作用**：RAG 数据未就绪时，也能全链路跑通 ①-⑧ 并可视化每个 Agent 输入输出。

## 1. 一键启动（推荐）

仓库根目录 `start.py`（跨平台，Windows PowerShell / WSL / Linux）：

```bash
# 交互主菜单（1=全部 2=仅ai 3=仅backend 4=仅frontend 5=停止 6=退出）
python start.py

# 免交互
python start.py --only all          # 启动全部（默认调试模式）
python start.py --only ai           # 只启动 ai-service
python start.py --prod              # 正式模式（自动起 chroma + mysql）
python start.py --stop              # 停止全部已启动服务
```

**脚本自动完成**：
- 读取根目录 `.env`（`ALIYUN_BAILIAN_API_KEY` 等）
- 设置 `RAG_MOCK_SAMPLES`（调试 true / 正式 false）、`VECTOR_DB`、端口
- 中间件：`docker compose up -d mysql`（backend 需要）、正式模式 `up -d chroma`
- ai-service / backend：无 jar 时自动 `mvn package -DskipTests` 后 `java -jar`
- frontend：首次自动 `npm install`，随后 `npm run dev`
- 各服务日志重定向 `logs/<服务>.log`；Ctrl+C 或 `--stop` 一键结束全部

> 依赖提示：需 `java`（JDK 17）、`node/npm`、`docker`（可省略，缺则相关服务自动跳过或提示）、`mvn`（无 jar 时才需要）。

## 2. 手动启动（备用）

```bash
# ai-service（默认 8081）
cd ai-service
mvn clean spring-boot:run
# 或打包后
mvn package -DskipTests && java -jar target/ai-service-0.1.0.jar

# backend（默认 8080，需 MySQL）
cd backend && mvn spring-boot:run

# frontend（默认 5173）
cd frontend && npm install && npm run dev
```

环境变量：可在 shell/IDEA 设置，或 `source .env`（Linux/WSL）后启动。

## 3. 两种调试路径

### 3.1 直连 ai-service（最简，看每个 Agent 效果）
**不需要 backend / MySQL / 前端**，直接用接口调试管线：

```bash
# 启动 ai-service（调试模式）
python start.py --only ai
# 发起管线
curl -X POST http://localhost:8081/pipeline/run -H "Content-Type: application/json" \
  -d '{"question":"如何提升水稻病害模型在跨地区小样本场景的泛化能力？"}'
# 拿到 runId 后，浏览器开可视化调试页（看 ①-⑧ 每个 Agent 输入/输出/状态）
# http://localhost:8081/pipeline/{runId}/debug
# 人在回路恢复
curl -X POST http://localhost:8081/pipeline/{runId}/resume -H "Content-Type: application/json" \
  -d '{"reviewComment":"通过"}'
```

### 3.2 走前端完整链路
`frontend(5173) → backend(8080) → ai-service(8081)`：
```bash
python start.py --only all     # 全部启动（调试模式，起 mysql）
```
前端 Vue Flow 调用 backend 转发接口（`/api/tasks/{id}/stream` 等），backend 再转发 ai-service。

## 4. 环境变量清单（`.env`）

| 变量 | 说明 | 调试默认 |
|---|---|---|
| `ALIYUN_BAILIAN_API_KEY` | 百炼 API Key（**必填**） | 需填写 |
| `RAG_MOCK_SAMPLES` | 调试模式开关（true=mock 样例，无需 RAG） | `true` |
| `QWEN_MODEL` / `QWEN_LIGHT_MODEL` / `QWEN_TURBO_MODEL` | 分级模型 | `qwen-max` / `qwen-plus` / `qwen-turbo` |
| `AI_SERVICE_PORT` | ai-service 端口 | `8081` |
| `BACKEND_PORT` | backend 端口 | `8080` |
| `VECTOR_DB` | chroma / milvus | `chroma` |
| `MYSQL_HOST/PORT/DATABASE/USER/PASSWORD` | MySQL | localhost/3306/ai_scientist/root/… |
| `EMBEDDING_*` | 灌库脚本用 embedding | dashscope/text-embedding-v3 |

> `.env` 不入 Git；`RAG_MOCK_SAMPLES` 通常不必手改——启动脚本会自动设置。

## 5. 常见问题

| 现象 | 处理 |
|---|---|
| 报错 `缺少 ALIYUN_BAILIAN_API_KEY` | 在 `.env` 填入真实百炼 Key（`sk-` 开头，值勿含空格/中文） |
| `mvn` 找不到 | 系统未装 Maven；`start.py` 会自动跳过 build（有 jar 则用），或先安装 Maven 放入 PATH |
| 端口 8081/8080 被占用 | 改 `.env` 对应端口，或停止占用进程（`python start.py --stop`） |
| 想用改动后的代码 | `mvn package -DskipTests` 重新打包（Windows 挂载盘需 `mvn clean package`） |
| 正式模式 `VECTOR_DB=milvus` | Milvus 检索暂未接入（TODO 丁贾峻），请先用 `chroma` |
| 看不到 ⑤⑥⑦/⑧ | 管线停在 ④ 后人大回路暂停点，`POST /pipeline/{runId}/resume` 继续 |
