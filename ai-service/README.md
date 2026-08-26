# ai-service — 多智能体服务（独立工程）

Spring Boot + LangChain4j，承载七 Agent DAG 管线、四库 RAG 检索与百炼平台 Qwen 调用。

## 职责

- 七 Agent 管线编排（DAG + State 状态流转）：问题理解 → 文献检索/知识发现/假设生成（并行）→ 人在回路 → 评估 → 实验设计 → 辩论 → 输出
- 四库 RAG 检索接口（论文库/方法库/数据集库/证据库，混合检索）
- 阿里云百炼平台 Qwen 系列模型调用封装
- SSE 事件流（Agent 状态实时推送，由业务后端转发给前端）

## 知识发现 Agent 接入

`KnowledgeDiscoveryAgent.discover(DiscoveryRequest)` 已实现通用三阶段流程：逐篇证据提取、跨论文比较、Research Gap 排序。调用方传入科研问题、可选领域、`topK` 和可选论文证据，返回结构化 `DiscoveryResult`，其中包含已知发现、局限、冲突、技术迁移机会、排序后的研究空白、Problem Statement、Paper Title、Paper Abstract 与真实来源标识。

- 优先使用 `DiscoveryRequest.evidence`，便于文献检索 Agent 将结果直接交给知识发现 Agent。
- 未传直接证据时，自动调用 `RagSearchService.search("papers", question, topK)`。
- 每条论文证据必须包含 `title`、`content`，以及 DOI、PMID、URL 中至少一种来源标识。
- 模型输出中的 `references` 和每个 Gap 的 `evidenceIds` 必须来自输入证据；虚构来源会直接失败。
- 三阶段模型响应必须是约定 JSON；无效 JSON 会在异常消息中标出失败阶段。

张睿在 `PipelineEngine` 中只需将问题理解/文献检索阶段的结构化结果组装为 `DiscoveryRequest` 后调用 `discover`。当前自动测试使用模拟百炼响应，不需要 API Key 或向量数据库即可运行。

## 快速开始

```bash
# 依赖中间件（MySQL / 向量库）由根目录 docker-compose 提供
docker compose up -d

# 配置环境变量（必填：百炼 API Key）
cp ../.env.example ../.env

mvn spring-boot:run          # 默认 http://localhost:8081
```

## 目录结构

```
src/main/java/com/aiscientist/ai/
├── AiServiceApplication.java   # 启动类
├── agent/                      # 七 Agent（占位，Prompt/@Tool 细节由智能体组设计）
│   ├── ProblemUnderstandingAgent.java
│   ├── LiteratureRetrievalAgent.java
│   ├── KnowledgeDiscoveryAgent.java
│   ├── HypothesisGenerationAgent.java
│   ├── HypothesisEvaluationAgent.java
│   ├── ExperimentDesignAgent.java
│   └── DebateAgent.java
├── pipeline/                   # DAG 编排与 State 管理（占位）
├── rag/                        # 四库检索接口（占位）
└── llm/                        # 百炼调用封装（占位）
```

## 依赖

- LangChain4j 0.35+（编排、@Tool/@AiService）
- langchain4j-open-ai（百炼 OpenAI 兼容接口）
- WebFlux（SSE 流）
- 向量库 Java SDK（Chroma / Milvus 按需启用）

> 设计文档：[docs/agents.md](../docs/agents.md)（管线）、[docs/rag.md](../docs/rag.md)（四库）、[docs/architecture.md](../docs/architecture.md)（架构）
