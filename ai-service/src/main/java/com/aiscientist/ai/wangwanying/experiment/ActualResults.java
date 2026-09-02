package com.aiscientist.ai.wangwanying.experiment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ActualResults(
        ExperimentExecutionStatus executionStatus,
        Map<String, Double> metricValues,
        List<String> formulaDerivation,
        List<String> artifactPaths,
        Instant startedAt,
        Instant finishedAt,
        String conclusion) {
}