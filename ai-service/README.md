# ai-service — 多智能体服务（独立工程）

Spring Boot + LangChain4j，承载八 Agent DAG 管线、四库 RAG 检索与百炼平台 Qwen 调用。

## 职责

- 八 Agent 管线编排（DAG + State 状态流转）：问题理解 → 文献检索/知识发现（并行）→ 假设生成 → 人在回路 → 评估 → 实验设计 → 辩论 → 报告生成 → 输出
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

### 最小调用示例

```java
DiscoveryRequest request = new DiscoveryRequest(
        "如何提升跨地区小样本识别的泛化能力？",
        "农业人工智能",
        List.of(
                new PaperEvidence(
                        "论文 A", "摘要或相关正文 A", List.of("作者 A"), 2025,
                        "10.1000/paper-a", null, null),
                new PaperEvidence(
                        "论文 B", "摘要或相关正文 B", List.of("作者 B"), 2024,
                        null, "12345678", null)
        ),
        5
);

DiscoveryResult result = knowledgeDiscoveryAgent.discover(request);
```

直接证据和 RAG 检索结果遵循相同的消费侧字段：`title`、`content`、`authors`、`year`、`doi`、`pmid`、`url`。其中 `title`、`content` 必填，DOI、PMID、URL 至少提供一个；这只是 Agent 输入契约，不限定向量库 collection/schema。

来源标识会在分析前规范化并去重：DOI 去掉 `doi:` 或 `https://doi.org/` 前缀并转为小写，PMID 去掉 `pmid:` 前缀，URL 去除首尾空白。跨论文发现至少需要两篇不同来源论文。

以下情况会立即失败，不会返回看似完整但无法追溯的结果：

- RAG 没有返回证据，或去重后少于两篇论文；
- 证据提取遗漏、重复或虚构输入来源；
- 任一阶段返回无效 JSON；
- 没有生成 Research Gap；
- Gap 或最终 References 使用虚构来源；
- 最终 References 未覆盖全部 Gap 的证据来源。

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
├── agent/                      # 八 Agent（Prompt/@Tool 细节由智能体组设计）
│   ├── ProblemUnderstandingAgent.java
│   ├── LiteratureRetrievalAgent.java
│   ├── KnowledgeDiscoveryAgent.java
│   ├── HypothesisGenerationAgent.java
│   ├── HypothesisEvaluationAgent.java
│   ├── ExperimentDesignAgent.java
│   ├── DebateAgent.java
│   └── ReportGenerationAgent.java
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
