# AGENTS.md — 给 AI 编码助手的仓库接入指南

> 如果你是被召唤来**为这个仓库写代码的 AI**（Claude / Cursor / Copilot / 其他 Agent），请先完整阅读本文件。
> 本文件是仓库的「接入宪法」：**先读它，再动手。**

---

## 1. 项目是什么

2026「挑战杯」揭榜挂帅大赛（XH-202619）参赛作品：基于国产开源大模型 **Qwen**（阿里云百炼平台）的 **AI Scientist**——一个「文献/数据输入 → 可验证科学假设输出」的多智能体系统。

- 后端：Spring Boot 3.x + Java 17 + **LangChain4j 1.18.1**（`ai-service`）
- 八 Agent DAG 管线 + 四库 RAG（论文/方法/数据/证据库）+ 人在回路
- 最终输出：10 字段《科学假设与研究计划》（**引用严禁虚构**，赛题硬性要求）

## 2. 必读文档（动手前按序读）

| 文档 | 内容 |
|---|---|
| `docs/agents.md` | **八 Agent 管线设计**（编排顺序、状态机、输出字段） |
| `docs/architecture.md` | 四层架构与通信链路 |
| `docs/rag.md` | 四库 RAG 设计（**灌库脚本已实现**，JSONL 输入契约 + collection 命名 + 检索对齐见第 6/7 节） |
| `docs/contribution.md` | 开发规范（分支/提交/代码规范） |
| `ai-service/src/main/java/com/aiscientist/ai/pipeline/` | **管线框架源码**（本文件配套代码） |

## 3. 仓库结构

```
ai-service/   多智能体服务（LangChain4j 八 Agent 管线）★ 大部分任务在这里
backend/      业务后端（REST API / SSE / MySQL）——表结构由团队定，勿固化
frontend/     Vue3 前端（人在回路交互）
data/         Python 数据处理脚本
docs/         全部设计文档
```

## 4. 八 Agent 管线编排顺序（严格遵守）

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
⑧ 报告生成 Agent（融合 ①-⑦ 产物，组装 10 字段《科学假设与研究计划》）
                      │
                      ▼
       输出 10 字段《科学假设与研究计划》
```

编排由 `PipelineEngine` 实现（①→②∥③并行→④→暂停→⑤→⑥→⑦→⑧→组装输出），**不需要你改编排逻辑**。

## 5. 你的核心任务：接入一个 Agent（可插拔）

**队友的 Agent 代码已写好（或你来实现），接入框架 = 实现 `PipelineAgent` 接口**：

```java
public interface PipelineAgent {
    AgentStage stage();                      // 声明属于哪个阶段（见 AgentStage 枚举）
    void execute(PipelineContext ctx) throws Exception;  // 读 ctx 输入，写 ctx 输出
}
```

**接入步骤**：

1. 读 `docs/agents.md` 找到对应阶段的职责与输出字段
2. 读 `pipeline/PipelineModels.java` 找到该阶段的输入/输出 record 契约
3. 实现 `PipelineAgent`（可直接实现，也可**包装已有类**——见下方模板示范）
4. 加 `@Component` 注解（Spring 自动收集，**无需手工注册**）
5. 写单元测试（mock LLM，仿照 `KnowledgeDiscoveryAgentTest`）
6. 遵守「提交前检查清单」

**模板示范**（已接入的 ③ 知识发现，包装队友马艺萌的实现，**不许改她的类**）：

```java
@Component
public class KnowledgeDiscoveryStage implements PipelineAgent {

    private final KnowledgeDiscoveryAgent agent;   // 马艺萌已实现的类

    public KnowledgeDiscoveryStage(KnowledgeDiscoveryAgent agent) {
        this.agent = agent;
    }

    @Override
    public AgentStage stage() { return AgentStage.KNOWLEDGE; }

    @Override
    public void execute(PipelineContext ctx) {
        // 读输入：问题 + ②文献检索产物（未实现时为空，走 RAG 回退）
        List<PaperEvidence> evidence = ctx.getLiterature() == null
                ? List.of() : ctx.getLiterature().papers();
        // 调用队友实现
        DiscoveryResult result = agent.discover(new DiscoveryRequest(
                ctx.getQuestion(), null, evidence, 5));
        // 写输出到数据总线
        ctx.setKnowledgeDiscovery(result);
    }
}
```

## 6. 数据契约（PipelineContext 字段 ↔ 阶段）

| 阶段 | 读（输入） | 写（输出字段） | record 类型 |
|---|---|---|---|
| ① 问题理解 | `getQuestion()` | `setQuestionQuery()` | `QuestionQuery` |
| ② 文献检索 | `getQuestionQuery()` | `setLiterature()` | `LiteratureResult`（papers 复用 `PaperEvidence`） |
| ③ 知识发现 | `getQuestion()`（**自足 RAG**，不读 ②，避免并行竞态） | `setKnowledgeDiscovery()` | `DiscoveryResult`（马艺萌，已接入） |
| ④ 假设生成 | `getKnowledgeDiscovery()` 的 selectedProblem/researchGaps 等 + `getLiterature()` | `setHypothesis()` | `HypothesisResult` |
| ⑤ 科学假设评估 | `getHypothesis()` | `setEvaluation()` | `EvaluationResult`（含幻觉检测） |
| ⑥ 实验设计 | `getEvaluation()` 的最优假设 | `setExperiment()` | `ExperimentResult` |
| ⑦ 思辨辩论 | `getEvaluation()` / `getExperiment()` | `setDebate()` | `DebateResult` |
| ⑧ 报告生成 | ①-⑦ 全部产物 | `setFinalReport()` | `ResearchPlan` |

**数据流主线**：`① 子查询 → ②∥③ 文献 + Gap/选题 → ④ 候选假设 → [人回路] → ⑤ 评分+幻觉检测 → ⑥ 实验方案 → ⑦ 辩论完善 → ⑧ 报告组装 → 10 字段报告`

## 7. 硬性规则（违反 = 打回）

1. **引用严禁虚构**（赛题红线）：所有 References / evidenceIds 必须来自真实输入来源（DOI/PMID/URL 白名单），仿照马艺萌的 `validateResultSources` 做白名单校验
2. **RAG 契约对齐**（如需检索）：四库灌库脚本已实现（`data/scripts/ingest_*.py`，Chroma/Milvus 双支持），collection 为 `papers/methods/datasets/evidence`；`RagSearchService` 返回对象字段必须与 `PaperEvidence` 对齐（title/content/doi/pmid/url/authors/year），否则 `convertValue` 转换失败；`source_id` 规范为 `doi:xxx` / `pmid:xxx` / `url:xxx`
3. **不越界**：只改自己的 Agent / 阶段文件，**不要**动：`PipelineEngine`、`PipelineContext`、`PipelineModels`、其他队友的 Agent 类、`RagSearchService`、`BailianClient`、向量库配置
4. **开发细节留白**：MySQL 表结构、接口请求响应字段——**由团队设计确定**，不要代为固化（向量库 collection 已随灌库脚本固化，见 docs/rag.md）
5. **密钥保密**：API Key 只放 `.env`（已 gitignore），绝不允许写进代码或提交
6. **遵循既有风格**：record 数据契约 + 构造器校验 + 不可变列表（仿 `PipelineModels`）；中文注释说明职责
7. **同步文档**：实现完成后更新 `docs/agents.md` 对应章节（参照已有 7.1 节写法）
8. **测试**：必须带单元测试（mock LLM 即可），提交前 `mvn test` 通过

## 8. 提交前检查清单

- [ ] 只实现了自己的阶段，未动框架核心与队友代码
- [ ] 引用了文献的字段都有来源标识，且做了白名单校验
- [ ] 已加 `@Component`，Spring 能自动收集
- [ ] 单元测试通过（8 个测试通过是当前基线，不得减少）
- [ ] `docs/agents.md` 已同步更新
- [ ] 提交信息符合规范：`<类型>(<范围>): <中文描述>`（如 `feat(agent): 接入假设生成 Agent`）
- [ ] 未提交任何密钥、`.env`、临时文件

---

> 疑问先查 `docs/` 与 `pipeline/` 源码；仍不确定时，宁可少改、不可臆造。
> 你在为比赛写代码，质量与可复现性直接决定评分（代码与结果可复现性 10 分）。
