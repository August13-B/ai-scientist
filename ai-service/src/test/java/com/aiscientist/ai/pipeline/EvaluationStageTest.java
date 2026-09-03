package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.verify.CitationVerifier;
import com.aiscientist.ai.verify.ExternalLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * EvaluationStage 测试：打回红线（无引用 / 无法核验）。
 */
class EvaluationStageTest {

    private CitationVerifier verifier;
    private BailianClient bailianClient;
    private EvaluationStage stage;

    @BeforeEach
    void setUp() {
        ExternalLookup lookup = mock(ExternalLookup.class);
        verifier = new CitationVerifier(lookup);
        bailianClient = mock(BailianClient.class);
        // 无 Key 时 BailianClient 抛异常 → 评估阶段回退启发式评分
        when(bailianClient.chat(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("缺少 API Key"));
        stage = new EvaluationStage(verifier, bailianClient, false);
    }

    private PipelineContext ctxWithHypothesis(List<String> evidenceIds) {
        PipelineContext ctx = new PipelineContext();
        ctx.setHypothesis(new PipelineModels.HypothesisResult(List.of(
                new PipelineModels.Hypothesis(
                        "测试假设", "首次提出创新方法",
                        List.of("Qwen"), List.of("基线 + 指标"),
                        List.of(), evidenceIds))));
        return ctx;
    }

    @Test
    void noReferencesShouldFail() {
        PipelineContext ctx = ctxWithHypothesis(List.of());
        assertThrows(IllegalStateException.class, () -> stage.execute(ctx),
                "无引用必须打回");
    }

    @Test
    void unverifiableReferenceShouldFail() {
        ExternalLookup lookup = mock(ExternalLookup.class);
        when(lookup.findByDoi(anyString())).thenReturn(ExternalLookup.Result.error());
        EvaluationStage s = new EvaluationStage(new CitationVerifier(lookup), bailianClient, false);
        PipelineContext ctx = ctxWithHypothesis(List.of("doi:10.1038/nature14539"));
        assertThrows(IllegalStateException.class, () -> s.execute(ctx),
                "无法核验的引用不能自动通过");
    }

    @Test
    void verifiedReferenceShouldPass() {
        ExternalLookup lookup = mock(ExternalLookup.class);
        when(lookup.findByDoi("10.1038/nature14539"))
                .thenReturn(ExternalLookup.Result.found("Deep learning"));
        EvaluationStage s = new EvaluationStage(new CitationVerifier(lookup), bailianClient, false);
        PipelineContext ctx = ctxWithHypothesis(List.of("doi:10.1038/nature14539"));
        s.execute(ctx);
        org.junit.jupiter.api.Assertions.assertNotNull(ctx.getEvaluation());
    }
}
