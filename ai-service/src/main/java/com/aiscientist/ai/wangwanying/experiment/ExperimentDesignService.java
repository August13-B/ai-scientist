package com.aiscientist.ai.wangwanying.experiment;

import com.aiscientist.ai.wangwanying.evidence.Evidence;
import com.aiscientist.ai.wangwanying.evidence.EvidenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExperimentDesignService {
    private final EvidenceRepository evidenceRepository;
    private final ExperimentPlanGenerator generator;
    private final ExperimentResultCalculator resultCalculator;

    @Autowired
    public ExperimentDesignService(
            EvidenceRepository evidenceRepository,
            ExperimentPlanGenerator generator,
            ExperimentResultCalculator resultCalculator) {
        this.evidenceRepository = evidenceRepository;
        this.generator = generator;
        this.resultCalculator = resultCalculator;
    }

    public ExperimentDesignService(EvidenceRepository evidenceRepository, ExperimentPlanGenerator generator) {
        this(evidenceRepository, generator, new ExperimentResultCalculator());
    }

    public ExperimentPlan design(ExperimentRequest request) {
        List<Evidence> evidence = evidenceRepository.search(
                request.hypothesis() + " " + request.outcome(), 5);
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("Experiment design requires retrieved evidence");
        }
        GeneratedExperimentContent content = generator.generate(request, evidence);
        validate(content);
        return new ExperimentPlan(
                request.taskId(), request.runId(), request.hypothesisId(), request.title(),
                request.hypothesis(), content.baselines(), content.metrics(), content.datasets(),
                content.procedure(), content.expectedResults(),
                resultCalculator.calculate(request.measurements()), evidence, content.risks());
    }

    private void validate(GeneratedExperimentContent content) {
        require(content, "Model did not return experiment content");
        requireList(content.baselines(), 3, "baselines");
        requireList(content.metrics(), 5, "metrics");
        requireList(content.datasets(), 3, "datasets");
        requireList(content.procedure(), 5, "procedure");
        requireList(content.expectedResults(), 3, "expectedResults");
        requireList(content.risks(), 3, "risks");
    }

    private void requireList(List<String> values, int minimum, String field) {
        if (values == null || values.size() < minimum
                || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException(field + " must contain at least " + minimum + " values");
        }
    }

    private void require(Object value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
    }
}