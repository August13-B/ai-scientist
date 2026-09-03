package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineModels.HypothesisResult;
import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.ResearchGap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HypothesisGenerationAgentTest {
    @Test
    void generatesEvidenceBoundHypothesesWithRagKnowledge() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(rag.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(rag.search(org.mockito.ArgumentMatchers.eq("methods"), anyString(),
                org.mockito.ArgumentMatchers.eq(5))).thenReturn(List.of(
                paper("纵向建模", "混合效应模型", "10.1000/method")));
        when(rag.search(org.mockito.ArgumentMatchers.eq("evidence"), anyString(),
                org.mockito.ArgumentMatchers.eq(5))).thenReturn(List.of(
                paper("压力证据", "睡眠波动与压力相关", "10.1000/evidence")));
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(validJson());
        HypothesisGenerationAgent agent = new HypothesisGenerationAgent(
                bailian, rag, new ObjectMapper(), false);
        HypothesisResult result = agent.generate(
                "如何提前识别压力风险？", "心理健康", discovery(),
                List.of(paper("睡眠研究", "睡眠节律研究", "10.1000/paper")));
        assertEquals(3, result.hypotheses().size());
        verify(rag).search(org.mockito.ArgumentMatchers.eq("methods"), anyString(),
                org.mockito.ArgumentMatchers.eq(5));
        verify(rag).search(org.mockito.ArgumentMatchers.eq("evidence"), anyString(),
                org.mockito.ArgumentMatchers.eq(5));
        ArgumentCaptor<String> input = ArgumentCaptor.forClass(String.class);
        verify(bailian).chat(anyString(), anyString(), input.capture());
        assertTrue(input.getValue().contains("doi:10.1000/evidence"));
    }

    @Test
    void rejectsFabricatedEvidenceId() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(rag.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(validJson().replace("doi:10.1000/paper", "doi:fake"));
        HypothesisGenerationAgent agent = new HypothesisGenerationAgent(
                bailian, rag, new ObjectMapper(), false);
        assertThrows(IllegalStateException.class, () -> agent.generate(
                "问题", "领域", discovery(),
                List.of(paper("论文", "证据", "10.1000/paper"))));
    }

    private static String validJson() {
        return """
                {"hypotheses":[
                  {"summary":"假设一","rationale":"依据一",
                   "technicalDetails":["纵向建模"],"methods":["混合效应模型"],
                   "reasoningChain":["证据到机制"],"evidenceIds":["doi:10.1000/paper"]},
                  {"summary":"假设二","rationale":"依据二",
                   "technicalDetails":["时序分析"],"methods":["交叉验证"],
                   "reasoningChain":["差异到预测"],"evidenceIds":["doi:10.1000/evidence"]},
                  {"summary":"假设三","rationale":"依据三",
                   "technicalDetails":["稳健估计"],"methods":["敏感性分析"],
                   "reasoningChain":["限制到改进"],"evidenceIds":["doi:10.1000/method"]}
                ]}
                """;
    }

    private static DiscoveryResult discovery() {
        return new DiscoveryResult(
                List.of("睡眠波动与压力有关"), List.of("缺少纵向验证"),
                List.of(), List.of("迁移时序模型"),
                List.of(new ResearchGap("缺少个体基线", List.of("doi:10.1000/paper"),
                        0.9, "可验证")), "识别个体压力风险", "压力风险研究", "纵向研究",
                List.of("doi:10.1000/paper"));
    }

    private static PaperEvidence paper(String title, String content, String doi) {
        return new PaperEvidence(title, content, List.of("作者"), 2026, doi, null, null);
    }
}
