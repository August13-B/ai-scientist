package com.aiscientist.ai.wangwanying.experiment;

import com.aiscientist.ai.wangwanying.evidence.Evidence;
import java.util.List;

public record ExperimentPlan(
        String taskId,
        String runId,
        String hypothesisId,
        String title,
        String hypothesis,
        List<String> baselines,
        List<String> metrics,
        List<String> datasets,
        List<String> procedure,
        List<String> expectedResults,
        ActualResults actualResults,
        List<Evidence> supportingEvidence,
        List<String> risks) {
}