# AI Scientist · 基于国产开源大模型的科学假设自动生成系统

> 2026「挑战杯」揭榜挂帅大赛 · 题目编号 XH-202619
> 基于国产开源大模型（Qwen）的 AI Scientist 的研发与应用

本项目面向科研场景，基于**千问（Qwen）开源大模型**与**阿里云百炼平台**，构建一个具备「文献/数据输入 → 可验证科学假设输出」能力的多智能体系统原型（AI Scientist），实现科研灵感流水线的自动化与智能化。

- 基座模型：Qwen-Max / Qwen-Plus / Qwen-Turbo（通过阿里云百炼平台调用）
- 系统形态：多智能体系统（Multi-Agent System），七 Agent DAG 管线 + 人在回路（Human-in-the-Loop）
- 研究方向：暂定「深度学习 × 固态硬盘（SSD）存储」（待团队最终确认）

---

## ✨ 核心能力

| 赛题能力项 | 系统实现 |
|---|---|
| ① 文献挖掘与事实提取 | 四库 RAG（论文库/方法库/数据集库/证据库）+ 混合检索，提取关键科学事实，避免断章取义 |
| ② 逻辑驱动的假设生成 | 归纳与演绎推理链，基于已知事实生成 3–5 个候选科学假设 |
| ③ 论证可行与多轮迭代 | 跨学科技术迁移挖掘、引用真实性核验（幻觉检测）、多轮迭代完善提案 |
| ④ 智能体思辨与人在回路 | 倡议者/质疑者结构化辩论；评估后系统暂停，人类导师可介入修改后继续 |

## 🏗️ 系统架构

```
┌────────────────────────────────────────────────────────────┐
│  前端交互层  Vue 3 + Vite + TypeScript + Element Plus       │
│  Vue Flow 思维链可视化 · SSE 流式推送 · 人在回路介入按钮      │
└──────────────────────────┬─────────────────────────────────┘
                           │ HTTP / SSE
┌──────────────────────────▼─────────────────────────────────┐
│  业务后端层  Spring Boot 3.x + Java 17（backend/）          │
│  RESTful API · 任务编排 · SSE 流式转发 · MySQL 持久化        │
└──────────────────────────┬─────────────────────────────────┘
                           │ 内部 HTTP
┌──────────────────────────▼─────────────────────────────────┐
│  多智能体服务层  Spring Boot + LangChain4j（ai-service/）    │
│  七 Agent DAG 管线 · @Tool/@AiService · State 状态流转       │
│  ①问题理解→②文献检索→③知识发现→④假设生成→⑤评估→⑥实验设计→⑦辩论│
└──────────────────────────┬─────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────┐
│  数据引擎层  Python + 向量数据库（Milvus/Chroma）+ MySQL      │
│  论文库 · 方法库 · 数据集库 · 证据库 · 混合检索（向量+BM25）   │
└────────────────────────────────────────────────────────────┘
```

**调用链**：用户提问 → 问题理解 Agent 拆解子查询 → 并行执行（文献检索/知识发现/假设生成）→ 人在回路暂停点 → 评估 Agent（评分 + 幻觉检测）→ 实验设计 Agent → 辩论 Agent → 输出《科学假设与研究计划》。

## 🛠️ 技术栈

| 层次 | 技术选型 | 说明 |
|---|---|---|
| 前端 | Vue 3 + Vite + TypeScript | Vue Flow 流程图、Element Plus UI |
| 前端关键功能 | SSE 流式 + 人在回路 | Agent 思考过程实时展示，人类可介入修改 |
| 业务后端 | Spring Boot 3.x + Java 17 | RESTful API + SSE 流式转发 + MySQL |
| AI 编排 | LangChain4j 0.35+ | DAG 编排、@Tool/@AiService、State 管理 |
| 模型调用 | 阿里云百炼 → Qwen 系列 | OpenAI 兼容 API；支持 SFT 微调 |
| 向量数据库 | Milvus（生产）/ Chroma（开发） | Java SDK 集成，混合检索 |
| 关系数据库 | MySQL 8.0 / PostgreSQL | 用户、任务记录、历史假设存档 |
| 向量化 | DashScope Embedding / BGE-M3 | 中文向量化，维度 768/1024 |
| 数据处理 | Python（pdfplumber / Grobid） | 批量 PDF 解析、数据清洗 |
| 部署 | Docker + Docker Compose | 一键部署，保证可复现性 |

## 📁 目录结构

```
.
├── frontend/          # 前端工程（Vue3 + Vite + TS + Vue Flow）
├── backend/           # 业务后端（Spring Boot：REST API / SSE / MySQL）
├── ai-service/        # 多智能体服务（LangChain4j：七 Agent 管线 / 四库检索 / 百炼调用）
├── data/              # 数据处理（Python 脚本：PDF 解析 / 清洗 / 灌库）
├── docs/              # 项目文档（架构 / Agent / RAG / 技术方案 / 接口 / 规范）
├── docker-compose.yml # 中间件编排（MySQL / Milvus / Chroma）
└── .env.example       # 环境变量模板（百炼 API Key 等）
```

## 🚀 快速开始

> 详细步骤见各子工程 README 与 `docs/`。

### 1. 环境变量

```bash
cp .env.example .env   # 填入 ALIYUN_BAILIAN_API_KEY 等
```

### 2. 启动中间件（MySQL / 向量数据库）

```bash
docker compose up -d
```

### 3. 启动多智能体服务（ai-service）

```bash
cd ai-service
mvn spring-boot:run
```

### 4. 启动业务后端（backend）

```bash
cd backend
mvn spring-boot:run
```

### 5. 启动前端（frontend）

```bash
cd frontend
npm install
npm run dev
```

## 📄 文档索引

| 文档 | 内容 |
|---|---|
| [docs/architecture.md](docs/architecture.md) | 系统架构设计（四层 + 通信链路） |
| [docs/agents.md](docs/agents.md) | 七 Agent 管线设计（DAG 状态流转 / 数据流 / 输出字段） |
| [docs/rag.md](docs/rag.md) | 四库 RAG 设计（论文/方法/数据/证据库） |
| [docs/tech-plan.md](docs/tech-plan.md) | 赛题技术方案（对标提交 PDF） |
| [docs/api-design.md](docs/api-design.md) | 接口清单（字段由后端组设计确定） |
| [docs/database.md](docs/database.md) | 数据库设计（预留，由后端组设计） |
| [docs/roadmap.md](docs/roadmap.md) | 六周 Sprint 计划与里程碑 |
| [docs/contribution.md](docs/contribution.md) | 开发规范（分支 / 提交 / 编码） |

## 📝 提交材料对照（赛题要求）

| 赛题要求 | 本项目对应 |
|---|---|
| 技术方案文档（PDF ≤ 20 页） | `docs/tech-plan.md` 整理导出 |
| 源代码 | `frontend/` `backend/` `ai-service/` `data/` |
| 可交互前端页面（可选加分） | `frontend/`（人在回路 + 思维链可视化） |
| 演示视频（≤ 10 分钟，可选） | 另行录制 |
| 百炼 API 调用凭证/截图 | 技术文档中附截图（丁贾峻负责） |

## ⚖️ License

[MIT](LICENSE)

