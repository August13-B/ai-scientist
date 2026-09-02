# 七 Agent 管线设计

> 更新时间：2026-08-05（初版）
> Agent 内部 Prompt 与 @Tool 实现细节由智能体组设计时确定，本文档只固化管线共识。

> 更新时间：2026-09-02（第二版：方案 B 调度——②∥③ 并行 → ④ 串行，人在回路在 ④ 后）

## 1. 管线总览

系统采用「串并结合」的编排策略：问题理解后**并行分发**到文献检索、知识发现两个 Agent（互不依赖、各自自足 RAG），聚合后串行执行假设生成，再经人在回路 → 评估 → 实验设计 → 辩论 → 输出。核心基于 **Java + LangChain4j**，使用 **DAG（有向无环图）** 逻辑管理 State 状态流转，各 Agent 封装为 `@Tool` 或 `@AiService`。

```
用户输入科研问题
    │
    ▼
① 问题理解 Agent ──拆解为结构化子查询（领域标签/关键概念/已知条件/待求解变量）
    │
    ├───────────┬───────────┐
    ▼           ▼           │
② 文献检索   ③ 知识发现    │  （并行执行，互不依赖，各自自足 RAG）
   Agent       Agent        │
    │           │           │
    └─────┬─────┴─────┬─────┘
          ▼           ▼
       ④ 假设生成 Agent（串行，消费 ③ 的 Gap/选题 + ② 的文献）
          │
          ▼
        【人在回路暂停点：人类介入审阅】← 前端 Vue Flow 展示
                      │
                      ▼
⑤ 科学假设评估 Agent（多维度评分 + 幻觉检测）
                      │
                      ▼
⑥ 实验设计 Agent（Baselines + Metrics + 数据集 + 预期结果）
                      │
                      ▼
⑦ 思辨辩论 Agent（倡议者 vs 质疑者 结构化辩论，多轮迭代）
                      │
                      ▼
       输出 10 字段《科学假设与研究计划》
```

## 2. Agent 职责定义

| # | Agent | 类名（占位） | 核心职责 | 主要输入库 | 关键输出 |
|---|---|---|---|---|---|
| ① | 问题理解 | `ProblemUnderstandingAgent` | 将自然语言科研问题拆解为结构化子查询，识别领域标签、关键概念、已知条件与待求解变量 | - | 子查询集合 |
| ② | 文献检索 | `LiteratureRetrievalAgent` | 基于子查询在论文库/证据库向量检索，召回 Top-K 文献，提取关键段落与引用链 | 论文库、证据库 | 文献列表 + 引用链 |
| ③ | 知识发现 | `KnowledgeDiscoveryAgent` | 三阶段跨文献分析：证据提取、论文比较、Research Gap 排序，并校验引用来源 | 论文库、方法库 | Problem Statement / Paper Title / Paper Abstract + 排序后的 Gap 与证据 |
| ④ | 假设生成 | `HypothesisGenerationAgent` | 归纳与演绎推理，基于已知事实生成 3–5 个候选假设，每个附带推理链条 | 方法库、论文库、证据库 | Rationale / Technical Details / Methods |
| ⑤ | 科学假设评估 | `HypothesisEvaluationAgent` | 多维度评分（创新性、可行性、引用真实性、数据可获得性）；幻觉检测：反向比对真实文献，虚构引用立即打回 | 证据库、论文库、数据集库 | 评分排序 + 幻觉检测报告 / References |
| ⑥ | 实验设计 | `ExperimentDesignAgent` | 为最优假设设计完整实验方案：Baselines、Metrics、拟用数据集、预期结果范围 | 方法库、数据集库 | Experiments / Results |
| ⑦ | 思辨辩论 | `DebateAgent` | 「倡议者」与「质疑者」两个子 Agent 结构化辩论，多轮迭代完善 | 全部知识库 | 最终完善版研究计划 |

## 3. 状态流转（State 设计）

> 具体 State 字段由张睿（流水线总管）设计时确定，以下为状态机骨架。

```
IDLE → UNDERSTANDING → RETRIEVING/KNOWLEDGE(并行) → AGGREGATED → HYPOTHESIS → WAITING_HUMAN(人在回路) → EVALUATING → DESIGNING → DEBATING → DONE
    └────────────────────────────── ERROR ──────────────────────┘
```

- 每个状态对应一个/一组 Agent 的执行
- `WAITING_HUMAN` 为人在回路暂停点：**④ 假设生成后**系统暂停（发布 `pipeline.pause`），等待人类导师审阅候选假设，通过 `POST /pipeline/{runId}/resume` 提交审阅意见（可附修改后的候选假设）后恢复
- 异常状态支持重试与断点恢复（由团队细化）

## 4. Agent 间数据流

```
用户输入 → ① 子查询
  → ② 文献列表(带引用) ∥ ③ 研究空白/选题（并行，自足 RAG）
  → ④ 候选假设(带推理链，消费 ③ 的 Gap + ② 的文献)
  → [人在回路] 人类审阅意见
  → ⑤ 评分排序 + 幻觉检测结果 + 真实 References
  → ⑥ 实验方案(实验设计/数据集/预期结果)
  → ⑦ 辩论纪要 + 最终版研究计划
  → 输出 10 字段《科学假设与研究计划》
```

## 5. 输出规范：10 字段《科学假设与研究计划》

系统最终输出必须包含以下标准化字段（对应赛题生成结果规范）：

| # | 字段 | 生成 Agent |
|---|---|---|
| 1 | 待研究问题（Problem Statement） | 知识发现 |
| 2 | 解决思路（Rationale） | 假设生成 |
| 3 | 必要的技术手段（Technical Details） | 假设生成 |
| 4 | 数据集（Datasets：Source 历史数据 + Target 拟采集数据） | 数据引擎 + 评估 |
| 5 | 标题（Paper Title） | 知识发现 |
| 6 | 摘要（Paper Abstract） | 知识发现 |
| 7 | 方法论（Methods） | 假设生成 |
| 8 | 实验设计（Experiments：Baselines + Metrics） | 实验设计 |
| 9 | 实验结果（Results：公式推导或模拟验证） | 实验设计 |
| 10 | 参考论文（References：真实文献，**严禁虚构**） | 评估（幻觉检测把关） |

## 6. 四库 RAG 在各 Agent 中的分工

| Agent | 使用知识库 |
|---|---|
| 文献检索 Agent | 论文库、证据库 |
| 知识发现 Agent | 论文库、方法库 |
| 假设生成 Agent | 方法库、论文库、证据库 |
| 评估 Agent | 证据库、论文库、数据集库 |
| 实验设计 Agent | 方法库、数据集库 |

## 7. 提示词工程（由团队细化）

- 每个 Agent 维护独立 System Prompt，含角色定义、任务目标、输出格式约束（JSON Schema）
- 幻觉检测 Prompt 内置「引用真实性核验」指令：要求输出每条引用的 DOI/PMID 并反向比对
- 配置化 Prompt 文件存放于 `ai-service/src/main/resources/prompts/`（目录待建）

### 7.1 知识发现 Agent（已实现）

知识发现 Agent 使用通用领域输入，不写死 SSD 或其他学科。其调用契约为：

1. 输入 `DiscoveryRequest(question, domain, evidence, topK)`；`evidence` 可为空。
2. 有直接证据时优先分析；否则调用论文库 `RagSearchService.search("papers", question, topK)`。
   **管线接入（方案 B）**：`KnowledgeDiscoveryStage` 与 ② 文献检索并行，为消除竞态恒传空 `evidence`，知识发现**自足 RAG** 检索，不再消费 ② 的 `LiteratureResult`。
3. 直接证据与 RAG 返回对象都需包含 `title`、`content` 及 DOI/PMID/URL 中至少一种来源；Agent 按规范化来源标识去重，至少需要两篇不同来源论文。
4. 三阶段分别输出 `EvidenceExtraction`、`CrossPaperAnalysis` 和 `DiscoveryResult`；证据提取必须逐篇一一覆盖输入来源。
5. 至少生成一个 Research Gap；最终 `references` 与每个 Gap 的 `evidenceIds` 只能引用输入论文的 DOI、PMID 或 URL，且 `references` 必须覆盖全部 Gap 来源。

下游假设生成 Agent 主要消费 `selectedProblem`、`researchGaps`、`knownFindings`、`limitations`、`conflicts`、`transferOpportunities`、`paperTitle` 和 `paperAbstract`。管线编排只负责传递这些结构化字段，不需要解析自然语言段落。
