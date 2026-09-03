package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.HypothesisGenerationAgent;
import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import com.aiscientist.ai.pipeline.PipelineModels.Hypothesis;
import com.aiscientist.ai.pipeline.PipelineModels.HypothesisResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HypothesisGenerationStageTest {
    @Test
    void mapsPipelineContextIntoAgentAndWritesResult() {
        HypothesisGenerationAgent agent = mock(HypothesisGenerationAgent.class);
        HypothesisResult expected = new HypothesisResult(List.of(
                new Hypothesis("假设", "依据", List.of("技术"), List.of("方法"),
                        List.of("推理"), List.of("doi:10.1000/a"))));
        when(agent.generate(anyString(), anyString(), any(DiscoveryResult.class), any()))
                .thenReturn(expected);
        PipelineContext context = new PipelineContext();
        context.setQuestion("研究问题");
        context.setQuestionQuery(new PipelineModels.QuestionQuery(
                "研究问题", "研究领域", List.of(), List.of(), List.of(), List.of()));
        context.setKnowledgeDiscovery(mock(DiscoveryResult.class));
        HypothesisGenerationStage stage = new HypothesisGenerationStage(agent);
        stage.execute(context);
        assertEquals(AgentStage.HYPOTHESIS, stage.stage());
        assertEquals(expected, context.getHypothesis());
        verify(agent).generate(anyString(), anyString(), any(DiscoveryResult.class), any());
    }
}
