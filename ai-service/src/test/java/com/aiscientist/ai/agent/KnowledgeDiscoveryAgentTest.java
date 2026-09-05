package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryRequest;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.ResearchGap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeDiscoveryAgentTest {

    private static final String EXTRACTION_JSON = """
            {"papers":[
              {"sourceId":"doi:10.1000/a","researchQuestion":"病害识别","methods":["CNN"],"findings":["田间图像有效"],"limitations":["单一地区"],"futureWork":["跨地区验证"]},
              {"sourceId":"doi:10.1000/b","researchQuestion":"病害识别","methods":["ViT"],"findings":["精度更高"],"limitations":["小样本不稳定"],"futureWork":["数据高效学习"]}
            ]}
            """;

    private static final String COMPARISON_JSON = """
            {"knownFindings":["视觉模型可识别病害"],"limitations":["地域泛化不足"],
             "conflicts":["小样本条件下模型结论不一致"],"transferOpportunities":["迁移自监督学习"]}
            """;

    private static final String RESULT_JSON = """
            {"knownFindings":["视觉模型可识别病害"],"limitations":["地域泛化不足"],
             "conflicts":["小样本条件下模型结论不一致"],
             "researchGaps":[{"gap":"跨地区小样本识别缺少统一验证","evidenceIds":["doi:10.1000/a","doi:10.1000/b"],"confidence":0.87,"rankingReason":"两篇论文共同支持且可验证"}],
             "selectedProblem":"如何提升水稻病害模型在跨地区小样本场景的泛化能力？",
             "paperTitle":"面向跨地区小样本的水稻病害识别",
             "paperAbstract":"研究跨地区小样本条件下的水稻病害识别方法。",
             "references":["doi:10.1000/a","doi:10.1000/b"]}
            """;

    @Test
    void discoversRankedGapThroughThreeEvidenceBoundStages() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSuccessfulStages(bailian);
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);
        DiscoveryRequest request = new DiscoveryRequest(
                "如何改进水稻病害识别？",
                "农业人工智能",
                List.of(
                        paper("论文 A", "田间图像实验", "DOI:10.1000/A"),
                        paper("论文 B", "小样本实验", "https://doi.org/10.1000/b")
                ),
                5
        );

        DiscoveryResult result = agent.discover(request);

        assertEquals("如何提升水稻病害模型在跨地区小样本场景的泛化能力？",
                result.selectedProblem());
        assertEquals("面向跨地区小样本的水稻病害识别", result.paperTitle());
        assertEquals(1, result.researchGaps().size());
        assertEquals(List.of("迁移自监督学习"), result.transferOpportunities());
        assertEquals(List.of("doi:10.1000/a", "doi:10.1000/b"), result.references());
        verifyNoInteractions(rag);

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(bailian, times(3)).chat(anyString(), anyString(), messages.capture());
        assertTrue(messages.getAllValues().stream()
                .allMatch(message -> message.contains("农业人工智能")));
        assertTrue(messages.getAllValues().get(0)
                .contains("\"sourceId\":\"doi:10.1000/a\""));
        assertTrue(messages.getAllValues().get(0)
                .contains("\"sourceId\":\"doi:10.1000/b\""));
    }

    @Test
    void loadsPapersFromRagWhenDirectEvidenceIsAbsent() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        stubSuccessfulStages(bailian);
        when(rag.search("papers", "如何改进水稻病害识别？", 5)).thenReturn(List.of(
                new PaperEvidence("论文 A", "田间图像实验", List.of("作者"), 2025,
                        "10.1000/a", null, null),
                new PaperEvidence("论文 B", "小样本实验", List.of("作者"), 2025,
                        "10.1000/b", null, null)
        ));
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        DiscoveryResult result = agent.discover(new DiscoveryRequest(
                "如何改进水稻病害识别？", "农业人工智能", List.of(), 5));

        assertEquals("面向跨地区小样本的水稻病害识别", result.paperTitle());
    }

    @Test
    void failsWhenRagReturnsNoEvidence() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(rag.search("papers", "研究问题", 3)).thenReturn(List.of());
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> agent.discover(new DiscoveryRequest(
                        "研究问题", "通用科研", List.of(), 3)));

        assertTrue(error.getMessage().contains("论文检索未返回"));
        verifyNoInteractions(bailian);
    }

    @Test
    void rejectsEvidenceWithoutTwoDistinctPaperSources() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);
        DiscoveryRequest request = new DiscoveryRequest(
                "研究问题", "通用科研",
                List.of(
                        paper("论文 A", "摘要 A", "DOI:10.1000/A"),
                        paper("论文 A 重复记录", "摘要 A", "https://doi.org/10.1000/a")
                ),
                5
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> agent.discover(request));

        assertEquals("知识发现至少需要两篇不同来源论文", error.getMessage());
        verifyNoInteractions(bailian, rag);
    }

    /** 校验失败时已改为确定性回退：必须返回合法结果（含 Gap 且 references 覆盖其证据）。 */
    private static void assertFallbackValid(DiscoveryResult result) {
        assertNotNull(result);
        assertFalse(result.researchGaps().isEmpty());
        ResearchGap gap = result.researchGaps().get(0);
        assertFalse(gap.evidenceIds().isEmpty());
        assertTrue(result.references().containsAll(gap.evidenceIds()));
    }

    @Test
    void reportsTheStageForMalformedModelJson() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn("{not-json");
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        // 严格校验/解析失败改为确定性回退：返回合法结果而非中断
        assertFallbackValid(agent.discover(requestWithEvidence()));
    }

    @Test
    void rejectsExtractionThatOmitsAnInputPaper() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        String incompleteExtraction = """
                {"papers":[
                  {"sourceId":"doi:10.1000/a","researchQuestion":"病害识别","methods":["CNN"],"findings":["有效"],"limitations":["单一地区"],"futureWork":["跨地区验证"]}
                ]}
                """;
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(incompleteExtraction);
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        // 未逐篇覆盖召回来源 → 回退为合法结果
        assertFallbackValid(agent.discover(requestWithEvidence()));
        verify(bailian, times(1)).chat(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsExtractionThatDuplicatesAnInputPaper() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        String duplicatedExtraction = EXTRACTION_JSON
                .replace("doi:10.1000/b", "doi:10.1000/a");
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(duplicatedExtraction);
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        // 重复某篇召回来源 → 回退为合法结果
        assertFallbackValid(agent.discover(requestWithEvidence()));
        verify(bailian, times(1)).chat(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsReferencesNotPresentInInputEvidence() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        String fabricatedResult = RESULT_JSON.replace("doi:10.1000/b", "doi:10.1000/fake");
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(EXTRACTION_JSON, COMPARISON_JSON, fabricatedResult);
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        // 引用了白名单外来源 → 回退为合法结果
        assertFallbackValid(agent.discover(requestWithEvidence()));
    }

    @Test
    void rejectsResultWithoutResearchGap() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        String resultWithoutGap = """
                {"knownFindings":["视觉模型可识别病害"],"limitations":["地域泛化不足"],
                 "conflicts":["小样本条件下模型结论不一致"],"researchGaps":[],
                 "selectedProblem":"如何提升跨地区泛化能力？","paperTitle":"跨地区病害识别",
                 "paperAbstract":"研究跨地区条件下的病害识别。",
                 "references":["doi:10.1000/a","doi:10.1000/b"]}
                """;
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(EXTRACTION_JSON, COMPARISON_JSON, resultWithoutGap);
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        // 无 Research Gap → 回退为合法结果（含 Gap）
        assertFallbackValid(agent.discover(requestWithEvidence()));
    }

    @Test
    void rejectsReferencesThatDoNotCoverGapEvidence() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        String incompleteReferences = RESULT_JSON.replace(
                "\"references\":[\"doi:10.1000/a\",\"doi:10.1000/b\"]",
                "\"references\":[\"doi:10.1000/a\"]");
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(EXTRACTION_JSON, COMPARISON_JSON, incompleteReferences);
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        // references 未覆盖 Gap 证据 → 回退为合法结果（覆盖关系仍成立）
        assertFallbackValid(agent.discover(requestWithEvidence()));
    }

    @Test
    void acceptsJsonWrappedInMarkdownCodeFences() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(
                        fenced(EXTRACTION_JSON),
                        fenced(COMPARISON_JSON),
                        fenced(RESULT_JSON)
                );
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper(), false);

        DiscoveryResult result = agent.discover(requestWithEvidence());

        assertEquals("面向跨地区小样本的水稻病害识别", result.paperTitle());
    }

    private static void stubSuccessfulStages(BailianClient bailian) {
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(EXTRACTION_JSON, COMPARISON_JSON, RESULT_JSON);
    }

    private static DiscoveryRequest requestWithEvidence() {
        return new DiscoveryRequest(
                "如何改进水稻病害识别？", "农业人工智能",
                List.of(
                        paper("论文 A", "田间图像实验", "10.1000/a"),
                        paper("论文 B", "小样本实验", "10.1000/b")
                ),
                5
        );
    }

    private static PaperEvidence paper(String title, String content, String doi) {
        return new PaperEvidence(title, content, List.of("作者"), 2025,
                doi, null, null);
    }

    private static String fenced(String json) {
        return "```json\n" + json + "\n```";
    }
}
