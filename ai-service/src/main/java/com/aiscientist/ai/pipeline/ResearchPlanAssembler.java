package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 10 字段《科学假设与研究计划》组装器。
 *
 * <p>从各阶段产物映射到赛题要求的标准化输出字段：
 * ① ③ 知识发现（1/5/6/10）、④ 假设生成（2/3/7）、⑥ 实验设计（8/9）。
 * 尚未产出的阶段字段以「待生成」占位，接入后自动填充。</p>
 *
 * <p>字段 2/3/7 优先采用 ⑤ 评估最优假设（rankings 第一的 summary 匹配），
 * 评估未产出时回退取假设列表首个，保证 ④ 接入后报告可见其产物。</p>
 */
final class ResearchPlanAssembler {

    private static final String PENDING = "待生成（对应阶段 Agent 接入后填充）";

    private ResearchPlanAssembler() {
    }

    static ResearchPlan assemble(PipelineContext ctx) {
        DiscoveryResult kd = ctx.getKnowledgeDiscovery();
        PipelineModels.EvaluationResult evaluation = ctx.getEvaluation();
        PipelineModels.ExperimentResult experiment = ctx.getExperiment();

        // 1. 待研究问题（③ 知识发现产出 selectedProblem）
        String problemStatement = kd != null && hasText(kd.selectedProblem())
                ? kd.selectedProblem()
                : PENDING;

        // 2. 解决思路 / 3. 技术手段 / 7. 方法论（④ 假设生成，优先评估最优假设）
        PipelineModels.Hypothesis hypothesis = selectedHypothesis(ctx);
        String rationale = hypothesis != null && hasText(hypothesis.rationale())
                ? hypothesis.rationale()
                : PENDING;
        List<String> technicalDetails = hypothesis != null && !hypothesis.technicalDetails().isEmpty()
                ? hypothesis.technicalDetails()
                : List.of(PENDING);
        List<String> methods = hypothesis != null && !hypothesis.methods().isEmpty()
                ? hypothesis.methods()
                : List.of(PENDING);
        // 5. 标题 / 6. 摘要（③ 知识发现产出）
        String paperTitle = kd != null && hasText(kd.paperTitle()) ? kd.paperTitle() : PENDING;
        String paperAbstract = kd != null && hasText(kd.paperAbstract()) ? kd.paperAbstract() : PENDING;
        // 9. 实验结果（⑥ 实验设计）
        String results = experiment != null && hasText(experiment.expectedResults())
                ? experiment.expectedResults()
                : PENDING;

        // 8. 实验设计（⑥ 实验设计）
        ResearchPlan.ExperimentPlan experimentPlan = experiment == null
                ? new ResearchPlan.ExperimentPlan(List.of(PENDING), List.of(PENDING))
                : new ResearchPlan.ExperimentPlan(experiment.baselines(), experiment.metrics());

        // 4. 数据集（数据引擎 + 评估）
        ResearchPlan.DatasetPlan datasetPlan = experiment == null
                ? new ResearchPlan.DatasetPlan(List.of(PENDING), List.of(PENDING))
                : new ResearchPlan.DatasetPlan(
                        experiment.datasets(),
                        List.of(
                                "从 Source 按设备与型号隔离划分目标域测试集",
                                "按时间先后构造独立测试窗口，避免未来信息泄漏"));

        // 10. 参考论文（⑤ 评估把关 / ③ 知识发现溯源），严禁虚构
        LinkedHashSet<String> referenceSet = new LinkedHashSet<>();
        if (evaluation != null) {
            referenceSet.addAll(evaluation.references());
        }
        if (kd != null) {
            referenceSet.addAll(kd.references());
        }
        List<String> references = referenceSet.isEmpty()
                ? List.of(PENDING)
                : List.copyOf(referenceSet);

        return new ResearchPlan(
                problemStatement,
                rationale,
                technicalDetails,
                datasetPlan,
                paperTitle,
                paperAbstract,
                methods,
                experimentPlan,
                results,
                references
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 选择最终报告采用的候选假设：
     * 评估已产出时取 rankings 第一（最优）summary 匹配项，否则回退假设列表首个。
     *
     * @return 选中的假设；④ 未产出时返回 null
     */
    private static PipelineModels.Hypothesis selectedHypothesis(PipelineContext ctx) {
        PipelineModels.HypothesisResult result = ctx.getHypothesis();
        if (result == null || result.hypotheses().isEmpty()) {
            return null;
        }
        List<PipelineModels.Hypothesis> hypotheses = result.hypotheses();
        PipelineModels.EvaluationResult evaluation = ctx.getEvaluation();
        if (evaluation != null && !evaluation.rankings().isEmpty()) {
            String bestSummary = evaluation.rankings().get(0).summary();
            return hypotheses.stream()
                    .filter(item -> item.summary().equals(bestSummary))
                    .findFirst()
                    .orElse(hypotheses.get(0));
        }
        return hypotheses.get(0);
    }
}
