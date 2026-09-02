package com.aiscientist.ai.wangwanying.experiment;

import java.util.List;

public record GeneratedExperimentContent(
        List<String> baselines,
        List<String> metrics,
        List<String> datasets,
        List<String> procedure,
        List<String> expectedResults,
        List<String> risks) {
}