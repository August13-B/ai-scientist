package com.aiscientist.ai.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;

/** Adapts experiment design output to the shared pipeline contract. */
@Component
public class ExperimentStage implements PipelineAgent {

    @Override
    public AgentStage stage() {
        return AgentStage.EXPERIMENT;
    }

    @Override
    public void execute(PipelineContext ctx) {
        PipelineModels.EvaluationResult evaluation = ctx.getEvaluation();
        if (evaluation == null) {
            throw new IllegalStateException("Experiment stage requires evaluation output");
        }

        List<String> baselines = List.of("standard baseline", "retrieval-augmented baseline");
        List<String> metrics = evaluation.rankings().stream()
                .map(ranking -> "overall=" + ranking.overall() + ", feasibility=" + ranking.feasibility())
                .toList();
        if (metrics.isEmpty()) {
            metrics = List.of("overall score");
        }
        List<String> datasets = evaluation.references().isEmpty()
                ? List.of("evaluation dataset to be supplied")
                : evaluation.references();
        String expectedResults = evaluation.rankings().isEmpty()
                ? "Expected results require an evaluated hypothesis."
                : evaluation.rankings().get(0).summary();
        ctx.setExperiment(new PipelineModels.ExperimentResult(
                baselines, metrics, datasets, expectedResults));
    }
}
