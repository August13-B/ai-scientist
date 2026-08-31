# Knowledge Discovery Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收紧知识发现模块的来源规范、跨论文证据覆盖和最终引用完整性，并补齐可直接接入的说明。

**Architecture:** 在现有三阶段流程入口集中规范化与去重论文来源，阶段输出后执行确定性集合校验。保留现有公开记录类型和依赖边界，不新增存储层、客户端或编排层。

**Tech Stack:** Java 17、Spring Boot、Jackson、JUnit 5、Mockito、Maven

**Spec:** `docs/superpowers/specs/2026-08-28-knowledge-discovery-hardening-design.md`

## Global Constraints

- 只修改知识发现模块、对应测试与接入文档。
- 不增加第三方依赖。
- 不实现论文解析、向量数据库、百炼客户端、DAG 管线、方法库或其他 Agent。
- 每项生产行为必须先有失败测试，再写最小实现。

---

### Task 1: 规范化论文来源标识

**Files:**
- Modify: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryModelsTest.java`
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryModels.java`

**Interfaces:**
- Consumes: `PaperEvidence(String title, String content, List<String> authors, Integer year, String doi, String pmid, String url)`
- Produces: `String PaperEvidence.sourceId()`，返回规范化的 `doi:`、`pmid:` 或 `url:` 标识。

- [ ] **Step 1: 写入来源规范化失败测试**

新增参数化含义等价的断言：`DOI:10.1000/ABC` 与 `https://doi.org/10.1000/abc` 都应得到 `doi:10.1000/abc`，`PMID:123456` 应得到 `pmid:123456`，URL 应去除首尾空白。

- [ ] **Step 2: 运行模型测试并确认失败**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryModelsTest test`

Expected: FAIL，因为当前 `sourceId()` 只执行 `trim()`。

- [ ] **Step 3: 写入最小规范化实现**

在 `PaperEvidence` 内增加私有字符串规范化逻辑，并维持 DOI > PMID > URL 的来源优先级。

- [ ] **Step 4: 运行模型测试并确认通过**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryModelsTest test`

Expected: PASS。

### Task 2: 去重并要求真实的跨论文输入

**Files:**
- Modify: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java`
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgent.java`

**Interfaces:**
- Consumes: `List<PaperEvidence> loadEvidence(DiscoveryRequest request)`
- Produces: 按规范化 `sourceId` 保序去重且至少包含两篇论文的证据列表。

- [ ] **Step 1: 写入少于两篇不同来源的失败测试**

构造两条等价 DOI 证据，断言 `discover` 抛出包含“至少两篇不同来源论文”的 `IllegalArgumentException`，并断言百炼客户端没有交互。

- [ ] **Step 2: 运行 Agent 测试并确认失败**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: FAIL，因为当前流程不会去重或拒绝单一来源。

- [ ] **Step 3: 写入最小保序去重和数量校验**

使用 `LinkedHashMap<String, PaperEvidence>` 按 `sourceId` 保留第一次出现的论文；少于两篇时在任何模型调用前抛出 `IllegalArgumentException`。

- [ ] **Step 4: 运行 Agent 测试并确认通过**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: PASS。

### Task 3: 校验证据提取完整性

**Files:**
- Modify: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java`
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgent.java`
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryPrompts.java`

**Interfaces:**
- Consumes: `EvidenceExtraction.papers()` 与输入来源集合。
- Produces: 每个输入来源恰好一条 `PaperAnalysis` 的运行时保证。

- [ ] **Step 1: 写入遗漏来源和重复来源失败测试**

分别让证据提取响应遗漏 `doi:10.1000/b`、重复 `doi:10.1000/a`，断言流程在第二次模型调用前以 `IllegalStateException` 失败。

- [ ] **Step 2: 运行 Agent 测试并确认失败**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: FAIL，因为当前校验只验证来源属于允许集合。

- [ ] **Step 3: 写入精确覆盖校验并收紧提示词**

比较提取来源列表的大小、唯一集合及输入集合；三者不满足一一对应时失败。提取提示词明确要求每个输入来源恰好输出一次。

- [ ] **Step 4: 运行 Agent 测试并确认通过**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: PASS。

### Task 4: 校验 Gap 和最终引用完整性

**Files:**
- Modify: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java`
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgent.java`
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryPrompts.java`

**Interfaces:**
- Consumes: `DiscoveryResult.researchGaps()`、每项 `evidenceIds()` 和 `references()`。
- Produces: 至少一个 Gap，且 `references` 覆盖全部 Gap 来源。

- [ ] **Step 1: 写入空 Gap 和引用未覆盖失败测试**

构造 `researchGaps:[]` 的结果，以及 Gap 使用两条来源但 `references` 只含一条来源的结果；断言两者均以 `IllegalStateException` 失败。

- [ ] **Step 2: 运行 Agent 测试并确认失败**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: FAIL，因为当前只校验引用是否属于输入集合。

- [ ] **Step 3: 写入最小完整性校验并收紧提示词**

拒绝空 `researchGaps`，合并全部 Gap 的 `evidenceIds`，要求 `references` 包含该集合。排序提示词明确要求至少一个 Gap 及完整 references。

- [ ] **Step 4: 运行 Agent 测试并确认通过**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: PASS。

### Task 5: 覆盖模型常见 JSON 包装并补齐接入说明

**Files:**
- Modify: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java`
- Modify: `ai-service/README.md`
- Modify: `docs/agents.md`

**Interfaces:**
- Consumes: 百炼返回的 Markdown fenced JSON 与公开 `DiscoveryRequest` 契约。
- Produces: 代码块响应通过现有解析逻辑，README 给出可复制的最小 Java 调用示例和全部失败条件。

- [ ] **Step 1: 写入 fenced JSON 行为测试**

让三个模拟响应分别用 ````json` 包裹，断言 `discover` 成功返回预期标题。该测试保护现有 `stripCodeFence` 行为，无需生产修改。

- [ ] **Step 2: 运行 Agent 测试并确认测试通过**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: PASS，证明现有兼容逻辑受到回归保护。

- [ ] **Step 3: 更新接入文档**

在 `ai-service/README.md` 添加 `DiscoveryRequest` 最小调用示例、来源规范化、至少两篇论文和失败条件；在 `docs/agents.md` 同步消费侧契约，不定义向量库 schema。

- [ ] **Step 4: 运行完整验证**

Run: `mvn -f ai-service/pom.xml test`

Expected: 全部测试 PASS。

Run: `git diff --check`

Expected: 无空白错误。

### Task 6: 提交并推送现有 PR

**Files:**
- Stage: 本计划涉及的知识发现代码、测试和文档。

**Interfaces:**
- Consumes: 已通过完整验证的工作树。
- Produces: `feature/mayimeng-knowledge-discovery` 上的新提交与更新后的 PR #5。

- [ ] **Step 1: 检查变更范围**

Run: `git status --short && git diff --stat && git diff`

Expected: 仅出现本设计规定的文件。

- [ ] **Step 2: 提交变更**

Run: `git add <本计划涉及文件>`

Run: `git commit -m "feat(agent): 收紧知识发现证据校验"`

- [ ] **Step 3: 推送功能分支**

Run: `git push origin feature/mayimeng-knowledge-discovery`

- [ ] **Step 4: 核对远端与 PR**

确认本地 SHA 与远端分支一致，PR #5 保持开放且可合并。
