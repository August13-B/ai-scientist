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

### 7.2 实验设计 Agent（已接入）

实验设计 Agent 通过 `ExperimentStage implements PipelineAgent` 接入统一流水线，声明阶段为 `AgentStage.EXPERIMENT`。Spring 自动收集该组件，`PipelineEngine` 按既定顺序调度；本模块不提供独立 Controller，对外请求统一由 `backend` 转发。

#### 输入契约

`ExperimentStage` 只读取 `PipelineContext#getEvaluation()`：

1. `rankings` 必须至少包含一条已评分假设；Stage 按 `overall` 选择分数最高的假设。
2. `references` 必须至少包含一个可追溯来源标识，格式限定为 `doi:...`、`pmid:...` 或 `http(s)://...`。
3. 无法追溯的普通字符串会在调用模型前被拒绝，防止模型基于虚构引用设计实验。
4. 研究领域优先读取 `PipelineContext#getQuestionQuery().domain()`；缺失时使用通用科学领域描述。

#### 调用方式

Stage 将最优假设、研究领域、主要结果变量和已验证来源构造成 `ExperimentRequest` 与 `Evidence`，然后调用 Spring 注入的 `ExperimentPlanGenerator`。生产实现为 `BailianExperimentPlanGenerator`，使用百炼 Qwen 生成结构化 JSON；Stage 不包含硬编码 baseline、指标、数据集或预期结果。

模型返回内容必须包含：

- 至少 3 个可复现实验基线；
- 至少 5 个指标，包括主要指标、统计不确定性、效应量、稳健性和资源成本；
- 至少 3 个真实数据集来源；
- 至少 5 个实验步骤；
- 至少 3 个待验证的预期结果；
- 至少 3 个实验风险。

模型返回非法 JSON 时生成器最多重试一次；连续失败或模型调用异常时终止当前阶段，不生成替代数据。

#### 输出契约

生成内容映射到 `PipelineModels.ExperimentResult`，并通过 `PipelineContext#setExperiment()` 写回：

| 字段 | 来源与语义 |
|---|---|
| `baselines` | 直接取生成器的 `baselines`，表示公平、可复现的对照方法。 |
| `metrics` | 直接取生成器的 `metrics`，表示主要指标及统计、稳健性和成本指标。 |
| `datasets` | 直接取生成器的 `datasets`；每项必须包含数据集名称及可追溯的 HTTP/HTTPS URL。参考文献不得冒充数据集。 |
| `expectedResults` | 将生成器的 `expectedResults` 合并为文本，描述预测范围或判定条件；不得复制假设摘要，也不得声称实验已经完成。 |

`procedure`、`risks`、任务编号、运行编号、证据详情及 `actualResults` 属于实验模块扩展信息，不改变共享 `ExperimentResult` 契约。没有真实测量值时，扩展结果状态为 `NOT_EXECUTED`；只有提交真实观测值后才计算指标。

#### 来源与语义校验

1. 输入引用必须带 DOI、PMID 或 URL，且只能来自评估阶段的白名单结果。
2. 数据集必须同时包含名称和 URL；缺少 URL 时拒绝写入流水线上下文。
3. `expectedResults` 必须为模型生成的预测范围或判定条件，不能为空，也不能与最优假设文本相同。
4. `datasets` 与 `references` 含义不同：前者是实验数据来源，后者是支持假设的论文或证据来源。
5. Prompt 禁止虚构论文、DOI、PMID、数据集和已经发生的实验结果。

#### 失败行为

以下情况会抛出异常并中止 `EXPERIMENT` 阶段：

- `evaluation` 缔失或没有已评分假设；
- 没有可追溯引用，或引用不符合 DOI/PMID/URL 格式；
- 模型调用失败或连续返回非法 JSON；
- 数据集为空或缺少可追溯 URL；
- 预期结果为空，或只是复制假设摘要；
- 生成字段数量不足或包含空值。

#### 测试

- `ExperimentStageTest`：验证最优假设输入、真实生成器调用、共享契约映射、引用及字段语义校验。
- `BailianExperimentPlanGeneratorTest`：直接 mock `ChatModel`，覆盖合法 JSON、非法 JSON 重试和上游模型异常。
- `ExperimentDesignServiceTest`：覆盖证据检索、生成器调用、方案组装、无证据拒绝以及未执行结果状态。

提交前执行：

```powershell
mvn -f ai-service/pom.xml verify --batch-mode --no-transfer-progress
```

### 7.3 假设生成 Agent（已接入）

假设生成阶段由 `HypothesisGenerationStage implements PipelineAgent` 接入统一流水线，
声明阶段为 `AgentStage.HYPOTHESIS`。它读取知识发现的 selectedProblem、Research Gap、
已知发现、限制与迁移机会，同时消费文献检索结果。

`HypothesisGenerationAgent` 复用统一 `RagSearchService`：检索 `methods` 与 `evidence`
collection；当上游没有直接文献时补查 `papers`。检索结果沿用既有 `PaperEvidence`
契约，不修改 RAG 公共服务和向量库配置。

模型通过 `BailianClient` 调用百炼 Qwen，输出 3–5 条 `PipelineModels.Hypothesis`。
每条假设必须包含 summary、rationale、technicalDetails、methods、reasoningChain 和
evidenceIds。所有 evidenceIds 必须来自知识发现 references、文献结果或 RAG 检索结果
的 DOI/PMID/URL 白名单；发现虚构或越界引用时阶段立即失败。

对应测试为 `HypothesisGenerationAgentTest` 与 `HypothesisGenerationStageTest`，覆盖
方法库/证据库检索、管线上下文映射、结构化输出和虚构证据拒绝。

### 7.4 问题理解 Agent（已接入）

问题理解阶段由 `ProblemUnderstandingStage implements PipelineAgent` 接入统一流水线，
声明阶段为 `AgentStage.UNDERSTANDING`（管线首步，串行最先执行）。它读取
`ctx.getQuestion()`，拆解为结构化子查询后写入 `ctx.setQuestionQuery()`。

`ProblemUnderstandingAgent` 调用百炼 Qwen（qwen-max 分级）输出 `QuestionQuery`：
`originalQuestion` / `domain` / `subQueries`（3~5 条问题拆解子查询，供 ② 逐条检索）/
`keyConcepts`（3~8 个，中英文均可）/ `knownConditions`（可空）/ `targetVariables`（可空）。

输出校验：subQueries 非空且 ≤8、keyConcepts 非空且 ≤10，每条非空；domain 缺失时
默认「通用科研」（不写死 SSD 等学科）；originalQuestion 以管线输入原文为准。校验通过后
规范化重建 QuestionQuery（触发 compact 构造器校验与不可变列表）。

下游消费：② 文献检索按 subQueries 逐条检索论文库/证据库；③ 知识发现、④ 假设生成取
domain 作为检索域。对应测试为 `ProblemUnderstandingAgentTest`（8 例），覆盖正常拆解、
domain 默认值、空条件容缺、结构打回（缺子查询/空概念）、无效 JSON、代码块包裹与原文
trim。

> 2026-09-02：张睿实现。本 Agent 校验不依赖 LLM 之外的引用，无虚构风险面。

### 7.5 文献检索 Agent（已接入）

文献检索阶段由 `LiteratureRetrievalStage implements PipelineAgent` 接入统一流水线，
声明阶段为 `AgentStage.LITERATURE`（与 ③ 知识发现在并行组，互不依赖）。它读取
① 的 `ctx.getQuestionQuery()`（subQueries 为空时退化为按原始问题检索），检索并提炼后
写入 `ctx.setLiterature()`（`LiteratureResult`）。

`LiteratureRetrievalAgent` 流程为「**RAG 检索增强 → LLM 提炼**」：

1. **检索**：对每条 subQuery 在论文库（papers）+ 证据库（evidence）各检索 topK=5
   （复用 `RagSearchService`，返回已对齐 `PaperEvidence` 契约）；按 `sourceId` 去重聚合，
   少于 2 篇不同来源直接判失败，超过 15 篇按首现序裁剪（保留子查询优先级）。
2. **提炼（动态路由）**：召回 ≤8 篇走单次批量（1 次 LLM 输出 keyFindings +
   citationChains）；>8 篇走两阶段——分组（≤5 篇/组）逐篇提炼 keyFindings →
   跨篇生成 citationChains，控制单次输入 token。
3. **白名单校验**：每条 `KeyFinding(finding, evidenceIds)` / `CitationChain(chain,
   evidenceIds)` 的 evidenceIds 必须 ∈ 召回 `sourceId`；keyFindings 须覆盖每一篇召回
   文献（防漏篇与虚构）。输出经规范化重建触发 compact 构造器。

`keyFindings` = 关键发现（绑定召回来源）；`citationChains` = 召回文献间**逻辑关联说明**
（如方法传承/理论支撑/结论互补冲突，绑定召回来源），非字面引用关系（向量库不存引用图）。

RAG 接口预留：检索唯一入口为 `RagSearchService.search(knowledgeBase, query, topK)`，
知识库范围常量 `KNOWLEDGE_BASES = {papers, evidence}`（② 分工），需按域扩展
methods/datasets 时改常量即可，检索/提炼/校验链路不变。

下游消费：④ 假设生成读 `papers` 作为直接文献证据；⑤ 评估可据 papers 做本地反向比对。
对应测试 `LiteratureRetrievalAgentTest`（9 例：去重聚合/兜底检索/两阶段路由/裁剪/
白名单/覆盖性/无效 JSON）与 `LiteratureRetrievalStageTest`（2 例：ctx 映射/① 缺失兜底）。

> 2026-09-02：张睿实现。本 Agent 随 PR #21（① 问题理解）之后接入，①② 契约闭环：
> ① 拆解 subQueries → ② 逐条检索提炼 → ④ 消费 papers 生成假设。

### 7.6 报告生成 Agent（已接入）

报告生成阶段由 `ReportStage implements PipelineAgent` 接入，声明阶段为 `AgentStage.REPORT`（⑧，串行最后）。它读取 ①-⑦ 全部产物，调用 `ReportGenerationAgent` 生成最终 10 字段《科学假设与研究计划》，写入 `ctx.setFinalReport()`。

**与单 Agent 直出的区别**：输入包含各环节的过程性推理——① 子查询/关键概念、② keyFindings/citationChains、③ researchGaps、④ reasoningChain、⑤ 评分/幻觉报告、⑥ 实验方案、⑦ 辩论纪要，据此生成一份**多 Agent 协作链路清晰**的《计划》（rationale 融入 ④ 推理与 ⑦ 辩论共识、results 融入 ⑥+⑦、references 走白名单）。

**红线**：references 仅接受真实引用白名单（⑤ 核验通过的 `evaluation.references()` ∪ ③ 溯源 `knowledgeDiscovery.references()`），生成后逐字段校验，缺失/空时用对应阶段产物兜底；LLM 调用失败时 `ReportStage` 自动回退 `ResearchPlanAssembler`（纯 Java 拼接），保证报告永不空缺、管线不中断。

`PipelineEngine` 在 ⑦ 之后执行 REPORT 阶段；若 REPORT 未接入则引擎兜底 `ResearchPlanAssembler.assemble` 产出报告（保底不缺 10 字段）。

对应测试 `ReportGenerationAgentTest`（5 例：10 字段生成/白名单过滤/缺 references 回退/字段缺失兜底/无效 JSON）与 `ReportStageTest`（2 例：ctx 生成/LLM 失败回退 assembler）。

> 2026-09-03：张睿实现。至此管线为 **八 Agent**（①-⑦ 原七 Agent + ⑧ 报告生成）。
