package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.wangwanying.evidence.Evidence;
import com.aiscientist.ai.wangwanying.evidence.EvidenceModality;
import com.aiscientist.ai.wangwanying.experiment.ExperimentPlanGenerator;
import com.aiscientist.ai.wangwanying.experiment.ExperimentRequest;
import com.aiscientist.ai.wangwanying.experiment.GeneratedExperimentContent;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Connects the real experiment generator to the shared pipeline contract. */
@Component
public class ExperimentStage implements PipelineAgent {

    private final ExperimentPlanGenerator generator;

    public ExperimentStage(ExperimentPlanGenerator generator) {
        this.generator = generator;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.EXPERIMENT;
    }

    @Override
    public void execute(PipelineContext ctx) {
        PipelineModels.EvaluationResult evaluation = requireEvaluation(ctx);
        PipelineModels.ScoredHypothesis best = evaluation.rankings().stream()
                .max(Comparator.comparingDouble(PipelineModels.ScoredHypothesis::overall))
                .orElseThrow(() -> new IllegalStateException("Experiment stage requires a ranked hypothesis"));
        List<Evidence> evidence = evaluation.references().stream()
                .map(this::toEvidence)
                .toList();
        if (evidence.isEmpty()) {
            throw new IllegalStateException("Experiment stage requires verified DOI, PMID, or URL references");
        }
        String domain = ctx.getQuestionQuery() == null ? "general science" : ctx.getQuestionQuery().domain();
        ExperimentRequest request = new ExperimentRequest(
                "pipeline-task", "pipeline-run", "best-hypothesis",
                "Experiment for selected hypothesis", domain, best.summary(), "primary outcome", null);
        GeneratedExperimentContent generated = generator.generate(request, evidence);
        validateGeneratedContent(generated, best.summary());
        ctx.setExperiment(new PipelineModels.ExperimentResult(
                generated.baselines(), generated.metrics(), generated.datasets(),
                String.join("; ", generated.expectedResults())));
    }

    private void validateGeneratedContent(GeneratedExperimentContent generated, String hypothesis) {
        if (generated == null) {
            throw new IllegalStateException("Experiment generator returned no content");
        }
        if (generated.datasets() == null || generated.datasets().isEmpty()) {
            throw new IllegalStateException("Experiment datasets must not be empty");
        }
        for (String dataset : generated.datasets()) {
            if (dataset == null || dataset.isBlank()
                    || !(dataset.contains("https://") || dataset.contains("http://"))) {
                throw new IllegalStateException(
                        "Each experiment dataset must include a name and traceable URL: " + dataset);
            }
        }
        if (generated.expectedResults() == null || generated.expectedResults().isEmpty()) {
            throw new IllegalStateException("Expected results must describe a predicted range or decision criterion");
        }
        for (String expected : generated.expectedResults()) {
            if (expected == null || expected.isBlank()
                    || expected.trim().equalsIgnoreCase(hypothesis.trim())) {
                throw new IllegalStateException(
                        "Expected results must be predictions, not a copy of the hypothesis");
            }
        }
    }
    private PipelineModels.EvaluationResult requireEvaluation(PipelineContext ctx) {
        if (ctx.getEvaluation() == null) {
            throw new IllegalStateException("Experiment stage requires evaluation output");
        }
        return ctx.getEvaluation();
    }

    private Evidence toEvidence(String source) {
        String normalized = source == null ? "" : source.trim();
        String lower = normalized.toLowerCase();
        String doi = lower.startsWith("doi:") ? normalized.substring(4) : "";
        String pmid = lower.startsWith("pmid:") ? normalized.substring(5) : "";
        String url = lower.startsWith("http://") || lower.startsWith("https://") ? normalized : "";
        if (doi.isBlank() && pmid.isBlank() && url.isBlank()) {
            throw new IllegalStateException("Untraceable experiment reference: " + normalized);
        }
        return new Evidence(
                "pipeline-task", "pipeline-run", normalized, EvidenceModality.TEXT,
                "verified source", "supports", "selected hypothesis", normalized,
                doi, pmid, normalized, 2026, "", null, url, 1.0, "", List.of("pipeline"));
    }
}