package com.aiscientist.ai.wangwanying.experiment;

import com.aiscientist.ai.wangwanying.evidence.Evidence;
import java.util.List;

public interface ExperimentPlanGenerator {
    GeneratedExperimentContent generate(ExperimentRequest request, List<Evidence> evidence);
}