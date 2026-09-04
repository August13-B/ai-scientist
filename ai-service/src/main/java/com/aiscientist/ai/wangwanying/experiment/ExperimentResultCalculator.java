package com.aiscientist.ai.wangwanying.experiment;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ExperimentResultCalculator {

    public ActualResults calculate(ExperimentMeasurements measurements) {
        List<String> formulas = List.of(
                "baselineErrorRate = baselineErrors / baselineTotal",
                "proposedErrorRate = proposedErrors / proposedTotal",
                "absoluteErrorReduction = baselineErrorRate - proposedErrorRate",
                "relativeErrorReduction = absoluteErrorReduction / baselineErrorRate（baselineErrorRate > 0时）");
        if (measurements == null) {
            return new ActualResults(
                    ExperimentExecutionStatus.NOT_EXECUTED,
                    Map.of(),
                    formulas,
                    List.of(),
                    null,
                    null,
                    "尚未提交真实实验观测值；当前仅提供可复算公式，不能声明实验结论。");
        }

        double baselineRate = divide(measurements.baselineErrors(), measurements.baselineTotal());
        double proposedRate = divide(measurements.proposedErrors(), measurements.proposedTotal());
        double absoluteReduction = baselineRate - proposedRate;
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("baselineErrorRate", baselineRate);
        values.put("proposedErrorRate", proposedRate);
        values.put("absoluteErrorReduction", absoluteReduction);
        if (baselineRate > 0) {
            values.put("relativeErrorReduction", absoluteReduction / baselineRate);
        }
        String conclusion = absoluteReduction > 0
                ? "观测数据中拟议方法错误率低于基线；仍需结合置信区间和显著性检验判断统计可靠性。"
                : "观测数据未显示拟议方法错误率低于基线，不能支持预设改进方向。";
        return new ActualResults(
                ExperimentExecutionStatus.CALCULATED_FROM_OBSERVATIONS,
                Map.copyOf(values),
                formulas,
                measurements.artifactPaths(),
                measurements.startedAt(),
                measurements.finishedAt(),
                conclusion);
    }

    private double divide(int numerator, int denominator) {
        return (double) numerator / denominator;
    }
}