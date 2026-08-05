# ai-service — 多智能体服务（独立工程）

Spring Boot + LangChain4j，承载七 Agent DAG 管线、四库 RAG 检索与百炼平台 Qwen 调用。

## 职责

- 七 Agent 管线编排（DAG + State 状态流转）：问题理解 → 文献检索/知识发现/假设生成（并行）→ 人在回路 → 评估 → 实验设计 → 辩论 → 输出
- 四库 RAG 检索接口（论文库/方法库/数据集库/证据库，混合检索）
- 阿里云百炼平台 Qwen 系列模型调用封装
- SSE 事件流（Agent 状态实时推送，由业务后端转发给前端）

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
