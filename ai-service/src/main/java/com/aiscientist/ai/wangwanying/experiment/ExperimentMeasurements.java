package com.aiscientist.ai.wangwanying.experiment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record ExperimentMeasurements(
        @Min(0) int baselineErrors,
        @Min(1) int baselineTotal,
        @Min(0) int proposedErrors,
        @Min(1) int proposedTotal,
        List<String> artifactPaths,
        @NotNull Instant startedAt,
        @NotNull Instant finishedAt) {

    public ExperimentMeasurements {
        artifactPaths = artifactPaths == null ? List.of() : List.copyOf(artifactPaths);
        if (baselineErrors > baselineTotal || proposedErrors > proposedTotal) {
            throw new IllegalArgumentException("错误样本数不能大于总样本数");
        }
        if (finishedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("finishedAt不能早于startedAt");
        }
    }
}