# 通用知识发现 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现一个不绑定具体学科、可独立测试、可被现有管线调用的三阶段知识发现 Agent。

**Architecture:** `KnowledgeDiscoveryAgent` 接收科研问题和可选论文证据；没有直接证据时复用 `RagSearchService`。它依次让现有 `BailianClient` 完成证据提取、跨论文比较和 Research Gap 排序，并用 Jackson 将每阶段 JSON 解析为强类型记录，同时校验最终引用只能来自输入证据。

**Tech Stack:** Java 17、Spring Boot 3.3.2、Jackson、JUnit 5、Mockito、现有 `BailianClient`/`RagSearchService`

**Spec:** `docs/superpowers/specs/2026-08-26-knowledge-discovery-design.md`

## Global Constraints

- 模块必须通用于任意科研领域，禁止写死 SSD 或其他领域。
- 不实现 Chroma/Milvus、Embedding、论文抓取、DAG 编排或其他 Agent。
- 不新增第三方依赖。
- 每项最终结论必须能追溯到输入论文的 DOI、PMID 或 URL。
- 无效输入和无效模型 JSON 必须明确失败。

---

### Task 1: 定义知识发现数据契约和输入校验

**Files:**
- Create: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryModels.java`
- Test: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryModelsTest.java`

**Interfaces:**
- Produces: `KnowledgeDiscoveryModels.DiscoveryRequest(String question, String domain, List<PaperEvidence> evidence, int topK)`
- Produces: `PaperEvidence(String title, String content, List<String> authors, Integer year, String doi, String pmid, String url)` with `sourceId()`
- Produces: `PaperAnalysis`, `EvidenceExtraction`, `CrossPaperAnalysis`, `ResearchGap`, and `DiscoveryResult`

- [ ] **Step 1: Write the failing validation test**

```java
@Test
void paperEvidenceRequiresTraceableSource() {
    assertThrows(IllegalArgumentException.class,
            () -> new PaperEvidence("论文", "摘要", List.of(), 2025, null, null, null));
}
```

- [ ] **Step 2: Run the test and verify failure**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryModelsTest test`

Expected: FAIL because `KnowledgeDiscoveryModels` does not exist.

- [ ] **Step 3: Implement the records and compact-constructor validation**

Use immutable Java records. Normalize nullable lists with `List.copyOf`, require non-blank question/title/content, require `topK > 0`, and make `sourceId()` prefer DOI, then PMID, then URL.

- [ ] **Step 4: Run the focused test**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryModelsTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```text
git add ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryModels.java ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryModelsTest.java
git commit -m "feat(agent): 定义知识发现数据契约"
```

### Task 2: 实现三阶段 Prompt 和 Agent 服务

**Files:**
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgent.java`
- Create: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryPrompts.java`
- Test: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java`

**Interfaces:**
- Consumes: `BailianClient.chat(String model, String systemPrompt, String userMessage)`
- Consumes: `RagSearchService.search(String knowledgeBase, String query, int topK)`
- Produces: `DiscoveryResult KnowledgeDiscoveryAgent.discover(DiscoveryRequest request)`

- [ ] **Step 1: Write a failing happy-path test**

Mock `RagSearchService` and `BailianClient`; provide two traceable papers and three valid JSON responses. Assert exactly three model calls, generic domain text is preserved, and returned `problemStatement`, `paperTitle`, `paperAbstract`, gaps and reference IDs match the last response.

- [ ] **Step 2: Run the focused test and verify failure**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: FAIL because the service method is not implemented.

- [ ] **Step 3: Add the smallest three prompt builders**

`KnowledgeDiscoveryPrompts` provides package-private static methods `extraction()`, `comparison()`, and `ranking()`. Each demands JSON only, forbids invented citations, states the exact record fields, and receives the domain from the request instead of embedding a fixed domain.

- [ ] **Step 4: Implement the orchestration**

Replace the placeholder interface with a Spring `@Service` class. Inject `BailianClient`, `RagSearchService`, and `ObjectMapper`; select direct evidence first and otherwise convert paper RAG results to `PaperEvidence`; call model `qwen-plus` for each stage; parse fenced or plain JSON; validate non-empty results after every stage.

- [ ] **Step 5: Enforce citation provenance**

Build the allowed set from `PaperEvidence.sourceId()`. Reject a final `DiscoveryResult` when any result reference or gap evidence ID is absent from that set.

- [ ] **Step 6: Run the focused tests**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```text
git add ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgent.java ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryPrompts.java ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java
git commit -m "feat(agent): 实现三阶段知识发现流程"
```

### Task 3: 覆盖失败路径和 RAG 兼容入口

**Files:**
- Modify: `ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java`
- Modify: `ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgent.java`

**Interfaces:**
- Consumes/produces: Task 2 的 `discover(DiscoveryRequest)`，不增加公开接口。

- [ ] **Step 1: Add failing tests for trust boundaries**

Add tests that assert: empty direct evidence calls `search("papers", question, topK)`; an empty RAG result fails; malformed JSON fails with the stage name; and a fabricated DOI in final output fails provenance validation.

- [ ] **Step 2: Run the focused tests and verify failure**

Run: `mvn -f ai-service/pom.xml -Dtest=KnowledgeDiscoveryAgentTest test`

Expected: at least the new malformed JSON/provenance assertions fail before the guards exist.

- [ ] **Step 3: Implement only the guards required by the tests**

Throw `IllegalArgumentException` for missing evidence and `IllegalStateException` for malformed stage output or invented citations. Preserve the original cause for JSON parsing errors.

- [ ] **Step 4: Run all ai-service tests**

Run: `mvn -f ai-service/pom.xml test`

Expected: PASS with no API key or vector database.

- [ ] **Step 5: Commit**

```text
git add ai-service/src/main/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgent.java ai-service/src/test/java/com/aiscientist/ai/agent/KnowledgeDiscoveryAgentTest.java
git commit -m "test(agent): 校验知识发现失败路径"
```

### Task 4: 补充团队接入说明并完成验证

**Files:**
- Modify: `ai-service/README.md`
- Modify: `docs/agents.md`

**Interfaces:**
- Documents: `KnowledgeDiscoveryAgent.discover(DiscoveryRequest)` and its direct-evidence/RAG fallback behavior.

- [ ] **Step 1: Document the minimal integration contract**

Add a short section showing construction fields, the three analysis phases, failure rules, and that `PipelineEngine` only needs to pass its structured question and retrieved papers into `discover`.

- [ ] **Step 2: Run final verification**

Run: `mvn -f ai-service/pom.xml test`

Run: `git diff --check`

Expected: tests PASS and `git diff --check` exits 0.

- [ ] **Step 3: Commit documentation**

```text
git add ai-service/README.md docs/agents.md
git commit -m "docs(agent): 说明知识发现模块接入方式"
```

- [ ] **Step 4: Push the feature branch**

Run: `git push -u origin feature/mayimeng-knowledge-discovery`

Expected: remote branch is created successfully for a PR targeting `develop`.
