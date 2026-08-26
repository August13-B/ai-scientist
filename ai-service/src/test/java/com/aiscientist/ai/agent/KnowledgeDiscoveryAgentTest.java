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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KnowledgeDiscoveryAgentTest {

    @Test
    void discoversRankedGapThroughThreeEvidenceBoundStages() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(
                """
                {"papers":[
                  {"sourceId":"doi:10.1000/a","researchQuestion":"病害识别","methods":["CNN"],"findings":["田间图像有效"],"limitations":["单一地区"],"futureWork":["跨地区验证"]},
                  {"sourceId":"doi:10.1000/b","researchQuestion":"病害识别","methods":["ViT"],"findings":["精度更高"],"limitations":["小样本不稳定"],"futureWork":["数据高效学习"]}
                ]}
                """,
                """
                {"knownFindings":["视觉模型可识别病害"],"limitations":["地域泛化不足"],
                 "conflicts":["小样本条件下模型结论不一致"],"transferOpportunities":["迁移自监督学习"]}
                """,
                """
                {"knownFindings":["视觉模型可识别病害"],"limitations":["地域泛化不足"],
                 "conflicts":["小样本条件下模型结论不一致"],
                 "researchGaps":[{"gap":"跨地区小样本识别缺少统一验证","evidenceIds":["doi:10.1000/a","doi:10.1000/b"],"confidence":0.87,"rankingReason":"两篇论文共同支持且可验证"}],
                 "selectedProblem":"如何提升水稻病害模型在跨地区小样本场景的泛化能力？",
                 "paperTitle":"面向跨地区小样本的水稻病害识别",
                 "paperAbstract":"研究跨地区小样本条件下的水稻病害识别方法。",
                 "references":["doi:10.1000/a","doi:10.1000/b"]}
                """
        );
        KnowledgeDiscoveryAgent agent = new KnowledgeDiscoveryAgent(
                bailian, rag, new ObjectMapper());
        DiscoveryRequest request = new DiscoveryRequest(
                "如何改进水稻病害识别？",
                "农业人工智能",
                List.of(
                        paper("论文 A", "田间图像实验", "10.1000/a"),
                        paper("论文 B", "小样本实验", "10.1000/b")
                ),
                5
        );

        DiscoveryResult result = agent.discover(request);

        assertEquals("如何提升水稻病害模型在跨地区小样本场景的泛化能力？",
                result.selectedProblem());
        assertEquals("面向跨地区小样本的水稻病害识别", result.paperTitle());
        assertEquals(1, result.researchGaps().size());
        assertEquals(List.of("doi:10.1000/a", "doi:10.1000/b"), result.references());
        verifyNoInteractions(rag);

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(bailian, times(3)).chat(anyString(), anyString(), messages.capture());
        assertTrue(messages.getAllValues().stream()
                .allMatch(message -> message.contains("农业人工智能")));
    }

    private static PaperEvidence paper(String title, String content, String doi) {
        return new PaperEvidence(title, content, List.of("作者"), 2025,
                doi, null, null);
    }
}
