package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.wangwanying.experiment.ExperimentPlanGenerator;
import com.aiscientist.ai.wangwanying.experiment.GeneratedExperimentContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExperimentStageTest {

    @Test
    void invokesRealGeneratorContractAndMapsItsOutput() {
        ExperimentPlanGenerator generator = mock(ExperimentPlanGenerator.class);
        when(generator.generate(any(), any())).thenReturn(new GeneratedExperimentContent(
                List.of("baseline-a", "baseline-b", "baseline-c"),
                List.of("accuracy", "confidence interval", "effect size", "stability", "cost"),
                List.of("dataset-name (https://example.org/dataset)"),
                List.of("prepare", "split", "train", "evaluate", "report"),
                List.of("error rate is expected to decrease", "confidence interval excludes zero", "cost remains bounded"),
                List.of("data leakage", "small sample", "domain shift")));
        PipelineContext context = contextWithEvaluation();

        new ExperimentStage(generator, false).execute(context);

        verify(generator).generate(any(), any());
        assertThat(context.getExperiment().baselines()).containsExactly(
                "baseline-a", "baseline-b", "baseline-c");
        assertThat(context.getExperiment().datasets())
                .containsExactly("dataset-name (https://example.org/dataset)");
        assertThat(context.getExperiment().expectedResults())
                .contains("error rate is expected to decrease");
    }

    @Test
    void rejectsUntraceableReferencesBeforeCallingGenerator() {
        ExperimentPlanGenerator generator = mock(ExperimentPlanGenerator.class);
        PipelineContext context = contextWithEvaluation();
        context.setEvaluation(new PipelineModels.EvaluationResult(
                context.getEvaluation().rankings(), List.of(), List.of("evidence-1")));

        assertThatThrownBy(() -> new ExperimentStage(generator, false).execute(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Untraceable");
    }

    @Test
    void rejectsDatasetWithoutTraceableUrl() {
        ExperimentPlanGenerator generator = mock(ExperimentPlanGenerator.class);
        when(generator.generate(any(), any())).thenReturn(new GeneratedExperimentContent(
                List.of("a"), List.of("m"), List.of("untraceable dataset"), List.of("p"),
                List.of("predicted range 5%-10%"), List.of("risk")));

        assertThatThrownBy(() -> new ExperimentStage(generator, false).execute(contextWithEvaluation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("traceable URL");
    }

    @Test
    void mockModeAllowsDatasetWithoutUrl() {
        // 调试模式（RAG_MOCK_SAMPLES=true）：允许「数据集选择标准」描述（无 URL），便于无 RAG 时跑通
        ExperimentPlanGenerator generator = mock(ExperimentPlanGenerator.class);
        when(generator.generate(any(), any())).thenReturn(new GeneratedExperimentContent(
                List.of("a", "b", "c"), List.of("m1", "m2", "m3", "m4", "m5"),
                List.of("包含作物病害图像的数据集，要求公开可获取且含健康与病害样本"), List.of("p"),
                List.of("predicted range 5%-10%"), List.of("risk")));

        PipelineContext mockCtx = contextWithEvaluation();
        new ExperimentStage(generator, true).execute(mockCtx);

        assertThat(mockCtx.getExperiment().datasets())
                .contains("包含作物病害图像的数据集，要求公开可获取且含健康与病害样本");
    }

    @Test
    void rejectsExpectedResultCopiedFromHypothesis() {
        ExperimentPlanGenerator generator = mock(ExperimentPlanGenerator.class);
        when(generator.generate(any(), any())).thenReturn(new GeneratedExperimentContent(
                List.of("a"), List.of("m"), List.of("Dataset (https://example.org/data)"), List.of("p"),
                List.of("RAG reduces unsupported answers"), List.of("risk")));

        assertThatThrownBy(() -> new ExperimentStage(generator, false).execute(contextWithEvaluation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a copy");
    }
    private PipelineContext contextWithEvaluation() {
        PipelineContext context = new PipelineContext();
        context.setQuestionQuery(new PipelineModels.QuestionQuery(
                "question", "computer science", List.of(), List.of(), List.of(), List.of()));
        PipelineModels.ScoredHypothesis scored = new PipelineModels.ScoredHypothesis(
                "RAG reduces unsupported answers", 0.8, 0.9, 0.95, 0.7, 0.85);
        context.setEvaluation(new PipelineModels.EvaluationResult(
                List.of(scored), List.of(), List.of("doi:10.1000/test")));
        return context;
    }
}