package com.aiscientist.ai.wangwanying.experiment;

import com.aiscientist.ai.wangwanying.evidence.Evidence;
import com.aiscientist.ai.wangwanying.evidence.EvidenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
public class ExperimentDesignService {
    private final EvidenceRepository evidenceRepository;
    private final ExperimentPlanGenerator generator;
    private final ExperimentResultCalculator resultCalculator;

    @Autowired
    public ExperimentDesignService(EvidenceRepository evidenceRepository, ExperimentPlanGenerator generator, ExperimentResultCalculator resultCalculator) {
        this.evidenceRepository = evidenceRepository;
        this.generator = generator;
            this.resultCalculator = resultCalculator;
    }

    public ExperimentDesignService(EvidenceRepository evidenceRepository, ExperimentPlanGenerator generator) {
        this(evidenceRepository, generator, new ExperimentResultCalculator());
    }

    public ExperimentPlan design(ExperimentRequest request) {
        List<Evidence> evidence = evidenceRepository.search(request.hypothesis() + " " + request.outcome(), 5);
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("证据不足：Milvus未召回支持该假设的文献，请先补充证据库");
        }
        GeneratedExperimentContent content = generator.generate(request, evidence);
        validate(content);
        return new ExperimentPlan(request.taskId(), request.runId(), request.hypothesisId(), request.title(), request.hypothesis(), content.baselines(), content.metrics(),
                content.datasets(), content.procedure(), content.expectedResults(), resultCalculator.calculate(request.measurements()), evidence, content.risks());
    }

    private void validate(GeneratedExperimentContent content) {
        require(content, "百炼未返回实验设计内容");
        requireList(content.baselines(), 3, "baselines");
        requireList(content.metrics(), 5, "metrics");
        requireList(content.datasets(), 3, "datasets");
        requireList(content.procedure(), 5, "procedure");
        requireList(content.expectedResults(), 3, "expectedResults");
        requireList(content.risks(), 3, "risks");
    }

    private void requireList(List<String> values, int minimum, String field) {
        if (values == null || values.size() < minimum || values.stream().anyMatch(v -> v == null || v.isBlank())) {
            throw new IllegalStateException("百炼返回字段" + field + "不完整，至少需要" + minimum + "项非空内容");
        }
    }

    private void require(Object value, String message) {
        if (value == null) throw new IllegalStateException(message);
    }
}