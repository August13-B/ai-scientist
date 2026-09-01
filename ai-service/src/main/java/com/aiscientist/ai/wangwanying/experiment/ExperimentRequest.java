package com.aiscientist.ai.wangwanying.experiment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record ExperimentRequest(
        @NotBlank String taskId,
        @NotBlank String runId,
        @NotBlank String hypothesisId,
        @NotBlank String title,
        @NotBlank String domain,
        @NotBlank String hypothesis,
        @NotBlank String outcome,
        @Valid ExperimentMeasurements measurements) {
    public ExperimentRequest(String title, String domain, String hypothesis, String outcome) {
        this("default-task", "default-run", "default-hypothesis", title, domain, hypothesis, outcome, null);
    }
}