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
        // 伪造证据 ID → 校验失败时改为确定性回退：返回 ≥2 个可溯源的最小合法假设
        HypothesisResult result = agent.generate(
                "问题", "领域", discovery(),
                List.of(paper("论文", "证据", "10.1000/paper")));
        assertTrue(result.hypotheses().size() >= 2);
        assertTrue(result.hypotheses().stream()
                .allMatch(h -> !h.evidenceIds().isEmpty()));
    }

    @Test
    void allowsMoreThanFiveHypothesesWithCap() {
        // 旧的 3-5 恰好约束：LLM 给 6 个会报错；现在下限 ≥2、上限 8 截断，6 个应放行
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(rag.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(rag.search(org.mockito.ArgumentMatchers.eq("methods"), anyString(),
                org.mockito.ArgumentMatchers.eq(5))).thenReturn(List.of(paper("m", "m", "10.1000/method")));
        when(rag.search(org.mockito.ArgumentMatchers.eq("evidence"), anyString(),
                org.mockito.ArgumentMatchers.eq(5))).thenReturn(List.of(paper("e", "e", "10.1000/evidence")));
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(validJson(6));
        HypothesisGenerationAgent agent = new HypothesisGenerationAgent(
                bailian, rag, new ObjectMapper(), false);

        HypothesisResult result = agent.generate("问题", "领域", discovery(), List.of());

        assertEquals(6, result.hypotheses().size(), "6 个假设应放行（旧约束 >5 报错）");
    }

    @Test
    void truncatesHypothesesBeyondCap() {
        BailianClient bailian = mock(BailianClient.class);
        RagSearchService rag = mock(RagSearchService.class);
        when(rag.search(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(rag.search(org.mockito.ArgumentMatchers.eq("methods"), anyString(),
                org.mockito.ArgumentMatchers.eq(5))).thenReturn(List.of(paper("m", "m", "10.1000/method")));
        when(rag.search(org.mockito.ArgumentMatchers.eq("evidence"), anyString(),
                org.mockito.ArgumentMatchers.eq(5))).thenReturn(List.of(paper("e", "e", "10.1000/evidence")));
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(validJson(10));
        HypothesisGenerationAgent agent = new HypothesisGenerationAgent(
                bailian, rag, new ObjectMapper(), false);

        HypothesisResult result = agent.generate("问题", "领域", discovery(), List.of());

        assertEquals(8, result.hypotheses().size(), "超过上限 8 应截断");
    }

    private static String validJson() {
        return validJson(3);
    }

    /** 生成指定数量的候选假设（重复假设一，仅用于测试上限截断/放行） */
    private static String validJson(int count) {
        StringBuilder hypotheses = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                hypotheses.append(",");
            }
            hypotheses.append("{\"summary\":\"假设" + i + "\",\"rationale\":\"依据\","
                    + "\"technicalDetails\":[\"纵向建模\"],\"methods\":[\"混合效应模型\"],"
                    + "\"reasoningChain\":[\"证据到机制\"],\"evidenceIds\":[\"doi:10.1000/paper\"]}");
        }
        return "{\"hypotheses\":[" + hypotheses + "]}";
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
