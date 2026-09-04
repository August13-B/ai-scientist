package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineModels.KeyFinding;
import com.aiscientist.ai.pipeline.PipelineModels.LiteratureResult;
import com.aiscientist.ai.pipeline.PipelineModels.QuestionQuery;
import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** ② 文献检索 Agent 测试：检索聚合去重 / 动态路由提炼 / 白名单校验（mock RAG 与 LLM）。 */
class LiteratureRetrievalAgentTest {

    private static final String Q = "如何提升水稻病害模型泛化能力？";
    private static final QuestionQuery QUERY = new QuestionQuery(
            Q, "农业人工智能",
            List.of("跨地区病害图像差异分析", "小样本识别方法"),
            List.of("水稻病害", "迁移学习"), List.of("标注有限"), List.of("泛化准确率"));

    // ==================== 检索聚合 ====================

    @Test
    void retrievesPerSubQueryDeduplicatesAndExtractsSinglePass() {
        // 2 子查询 × 2 库，含重复 sourceId → 去重 5 篇 → ≤8 走单次批量提炼
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSearch(rag, "跨地区病害图像差异分析", List.of(p("p1"), p("p2")));
        stubSearch(rag, "小样本识别方法", List.of(p("p3"), p("p4")));
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(singlePassJson());
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);

        LiteratureResult result = agent.retrieve(QUERY);

        assertEquals(4, result.papers().size(), "应去重合并 4 篇不同来源");
        assertEquals(2, result.keyFindings().size());
        assertEquals(1, result.citationChains().size());
        assertTrue(result.papers().stream().map(PaperEvidence::sourceId)
                .collect(Collectors.toSet()).containsAll(
                        result.keyFindings().get(0).evidenceIds()));
        // 每条子查询 × 2 库 = 4 次检索
        verify(rag, times(4)).search(anyString(), anyString(), anyInt());
    }

    @Test
    void failsWhenFewerThanTwoDistinctSources() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSearch(rag, "跨地区病害图像差异分析", List.of(p("p1")));
        stubSearch(rag, "小样本识别方法", List.of(p("p1"))); // 同一来源重复
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> agent.retrieve(QUERY));

        assertTrue(error.getMessage().contains("两篇不同来源"));
        verifyNoInteractions(bailian);
    }

    @Test
    void fallsBackToOriginalQuestionWhenSubQueriesEmpty() {
        // ① 缺失兜底：subQueries 空 → 按 originalQuestion 检索
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(rag.search(eq("papers"), eq(Q), eq(5))).thenReturn(List.of(p("p1"), p("p2"), p("p3")));
        when(rag.search(eq("evidence"), eq(Q), eq(5))).thenReturn(List.of(p("p4")));
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(singlePassJson());
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);
        QuestionQuery fallback = new QuestionQuery(Q, "通用科研", List.of(), List.of(), List.of(), List.of());

        LiteratureResult result = agent.retrieve(fallback);

        assertEquals(4, result.papers().size());
        verify(rag).search(eq("papers"), eq(Q), eq(5));
    }

    // ==================== 动态路由：>8 篇两阶段 ====================

    @Test
    void routesToTwoStageExtractionWhenMoreThanEightPapers() {
        // 9 篇不同来源 → 分组（5+4）逐篇提炼 + 跨篇关联，共 3 次 LLM 调用
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(rag.search(eq("papers"), eq(Q), eq(5)))
                .thenReturn(List.of(p("p1"), p("p2"), p("p3"), p("p4"), p("p5")));
        when(rag.search(eq("evidence"), eq(Q), eq(5)))
                .thenReturn(List.of(p("p6"), p("p7"), p("p8"), p("p9"), p("p1"))); // p1 重复
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(
                        findingsJson("p1", "p2", "p3", "p4", "p5"),
                        findingsJson("p6", "p7", "p8", "p9"),
                        chainsJson("p1", "p3", "p6"));
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);
        QuestionQuery single = new QuestionQuery(Q, "农业人工智能",
                List.of(Q), List.of(), List.of(), List.of());

        LiteratureResult result = agent.retrieve(single);

        assertEquals(9, result.papers().size());
        assertEquals(2, result.keyFindings().size(), "两组提炼结果应合并");
        assertEquals(1, result.citationChains().size());
        verify(bailian, times(3)).chat(anyString(), anyString(), anyString());
    }

    @Test
    void trimsToMaxFifteenPapers() {
        // 17 篇不同来源 → 裁剪 15 → 两阶段（分组 3 次 + 跨篇 1 次 = 4 次 LLM）
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        List<PaperEvidence> papers9 = range("a", 9);
        List<PaperEvidence> evidence8 = range("k", 8);
        when(rag.search(eq("papers"), eq(Q), eq(5))).thenReturn(papers9);
        when(rag.search(eq("evidence"), eq(Q), eq(5))).thenReturn(evidence8);
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(
                        findingsJson("a0", "a1", "a2", "a3", "a4"),
                        findingsJson("a5", "a6", "a7", "a8", "k0"),
                        findingsJson("k1", "k2", "k3", "k4", "k5"),
                        chainsJson("a0", "k5"));
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);
        QuestionQuery single = new QuestionQuery(Q, "通用科研",
                List.of(Q), List.of(), List.of(), List.of());

        LiteratureResult result = agent.retrieve(single);

        assertEquals(15, result.papers().size());
        assertEquals("doi:10.1000/a0", result.papers().get(0).sourceId());
        assertEquals("doi:10.1000/k5", result.papers().get(14).sourceId());
        verify(bailian, times(4)).chat(anyString(), anyString(), anyString());
    }

    // ==================== 白名单与覆盖校验 ====================

    @Test
    void discardsChainThatOnlyReferencesUnretrievedSources() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSearch(rag, "跨地区病害图像差异分析", List.of(p("p1"), p("p2")));
        stubSearch(rag, "小样本识别方法", List.of(p("p3"), p("p4")));
        // LLM 在逻辑关联中引用了未召回的 doi:10.1000/fake
        String json = singlePassJson().replace(
                "{\"chain\":\"两方向文献共同指向泛化瓶颈\",\"evidenceIds\":[\"doi:10.1000/p1\",\"doi:10.1000/p3\"]}",
                "{\"chain\":\"两方向文献共同指向泛化瓶颈\",\"evidenceIds\":[\"doi:10.1000/fake\"]}");
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(json);
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);

        LiteratureResult result = agent.retrieve(QUERY);

        assertTrue(result.citationChains().isEmpty());
        assertTrue(result.keyFindings().stream()
                .flatMap(item -> item.evidenceIds().stream())
                .noneMatch(id -> id.contains("fake")));
    }

    @Test
    void removesFabricatedFindingAndCompletesFromRetrievedPapers() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSearch(rag, "跨地区病害图像差异分析", List.of(p("p1"), p("p2")));
        stubSearch(rag, "小样本识别方法", List.of(p("p3"), p("p4")));
        String json = """
                {"keyFindings":[
                  {"finding":"模型虚构的结论","evidenceIds":["doi:10.1000/fake"]},
                  {"finding":"真实发现与混合引用","evidenceIds":["doi:10.1000/p1","doi:10.1000/fake"]}],
                 "citationChains":[]}
                """;
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(json);
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);

        LiteratureResult result = agent.retrieve(QUERY);

        assertTrue(result.keyFindings().stream()
                .noneMatch(item -> item.finding().contains("虚构")));
        assertTrue(result.keyFindings().stream()
                .flatMap(item -> item.evidenceIds().stream())
                .noneMatch(id -> id.contains("fake")));
        assertTrue(result.keyFindings().stream()
                .flatMap(item -> item.evidenceIds().stream())
                .collect(Collectors.toSet())
                .containsAll(List.of("doi:10.1000/p1", "doi:10.1000/p2",
                        "doi:10.1000/p3", "doi:10.1000/p4")));
    }

    @Test
    void deterministicallyCompletesExtractionThatMissesAPaper() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSearch(rag, "跨地区病害图像差异分析", List.of(p("p1"), p("p2")));
        stubSearch(rag, "小样本识别方法", List.of(p("p3"), p("p4")));
        // keyFindings 只覆盖 p1,p2,p3，漏掉 p4
        String json = """
                {"keyFindings":[
                  {"finding":"发现一","evidenceIds":["doi:10.1000/p1","doi:10.1000/p2"]},
                  {"finding":"发现二","evidenceIds":["doi:10.1000/p3"]}],
                 "citationChains":[]}
                """;
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(json);
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);

        LiteratureResult result = agent.retrieve(QUERY);

        assertEquals(3, result.keyFindings().size());
        assertTrue(result.keyFindings().stream()
                .anyMatch(item -> item.evidenceIds().contains("doi:10.1000/p4")));
        assertTrue(result.keyFindings().stream()
                .anyMatch(item -> item.finding().contains("论文 p4")));
    }

    @Test
    void malformedModelJsonFallsBackToDeterministicFindings() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSearch(rag, "跨地区病害图像差异分析", List.of(p("p1"), p("p2")));
        stubSearch(rag, "小样本识别方法", List.of(p("p3"), p("p4")));
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn("{not-json");
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), false);

        // 生产模式：LLM 反复返回无效 JSON 时不再中断，而是确定性回退到逐篇保守补全，
        // 仍产出可溯源发现（覆盖全部召回文献），保证文献检索不因模型抖动中断。
        LiteratureResult result = agent.retrieve(QUERY);
        assertEquals(4, result.papers().size());
        assertEquals(4, result.keyFindings().size(), "回退补全需覆盖全部召回文献");
        assertTrue(result.keyFindings().stream()
                .flatMap(f -> f.evidenceIds().stream())
                .collect(Collectors.toSet())
                .containsAll(List.of("doi:10.1000/p1", "doi:10.1000/p2",
                        "doi:10.1000/p3", "doi:10.1000/p4")));
    }

    @Test
    void rejectsNullQuestionQuery() {
        BailianClient bailian = mock(BailianClient.class);
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, mock(RagSearchService.class), new ObjectMapper(), false);

        assertThrows(IllegalArgumentException.class, () -> agent.retrieve(null));
    }

    @Test
    void mockModeAllowsEmptyKeyFindings() {
        // 调试模式（RAG_MOCK_SAMPLES=true）：LLM 未提炼出关键发现时放行，不影响下游（④ 消费 papers）
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSearch(rag, "跨地区病害图像差异分析", List.of(p("p1"), p("p2")));
        stubSearch(rag, "小样本识别方法", List.of(p("p3"), p("p4")));
        // LLM 返回 keyFindings 为空（mock 样例未提炼出发现）
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn("{\"keyFindings\":[],\"citationChains\":[]}");
        LiteratureRetrievalAgent agent = new LiteratureRetrievalAgent(
                bailian, rag, new ObjectMapper(), true);

        LiteratureResult result = agent.retrieve(QUERY);

        assertEquals(4, result.papers().size());
        assertEquals(0, result.keyFindings().size(), "调试模式下允许空关键发现");
    }

    // ==================== 工具 ====================

    private static void stubSearch(RagSearchService rag, String query, List<PaperEvidence> papers) {
        when(rag.search(eq("papers"), eq(query), eq(5))).thenReturn(papers);
        when(rag.search(eq("evidence"), eq(query), eq(5))).thenReturn(papers);
    }

    private static PaperEvidence p(String key) {
        return new PaperEvidence("论文 " + key, "摘要 " + key + " 的实验内容", List.of("作者"), 2025,
                "10.1000/" + key, null, null);
    }

    private static List<PaperEvidence> range(String prefix, int count) {
        List<PaperEvidence> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(p(prefix + i));
        }
        return list;
    }

    /** 单次批量提炼响应（覆盖 p1..p4） */
    private static String singlePassJson() {
        return """
                {"keyFindings":[
                  {"finding":"田间图像对病害识别有效","evidenceIds":["doi:10.1000/p1","doi:10.1000/p2"]},
                  {"finding":"小样本影响模型稳定性","evidenceIds":["doi:10.1000/p3","doi:10.1000/p4"]}],
                 "citationChains":[
                  {"chain":"两方向文献共同指向泛化瓶颈","evidenceIds":["doi:10.1000/p1","doi:10.1000/p3"]}]}
                """;
    }

    /** 分组逐篇提炼响应：一条 finding 覆盖组内全部 sourceId */
    private static String findingsJson(String... keys) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append("\"doi:10.1000/").append(keys[i]).append("\"");
        }
        return "{\"keyFindings\":[{\"finding\":\"分组发现\",\"evidenceIds\":["
                + ids + "]}]}";
    }

    private static String chainsJson(String... keys) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append("\"doi:10.1000/").append(keys[i]).append("\"");
        }
        return "{\"citationChains\":[{\"chain\":\"文献间存在逻辑关联\",\"evidenceIds\":["
                + ids + "]}]}";
    }
}
