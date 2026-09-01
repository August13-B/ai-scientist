package com.aiscientist.ai.pipeline;

import org.junit.jupiter.api.Test;
import dev.langchain4j.model.chat.ChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiscientist.ai.wangwanying.experiment.BailianExperimentPlanGenerator;
import com.aiscientist.ai.wangwanying.experiment.ExperimentRequest;
import com.aiscientist.ai.wangwanying.evidence.Evidence;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExperimentStageTest {
    @Test
    void mapsEvaluationToSharedExperimentContract() {
        PipelineContext context = new PipelineContext();
        PipelineModels.ScoredHypothesis scored = new PipelineModels.ScoredHypothesis(
                "RAG reduces unsupported answers", 0.8, 0.9, 0.95, 0.7, 0.85);
        context.setEvaluation(new PipelineModels.EvaluationResult(
                List.of(scored), List.of(), List.of("evidence-1")));
        new ExperimentStage().execute(context);
        assertThat(context.getExperiment()).isNotNull();
        assertThat(context.getExperiment().baselines()).hasSize(2);
        assertThat(context.getExperiment().metrics()).isNotEmpty();
        assertThat(context.getExperiment().datasets()).containsExactly("evidence-1");
    }
}