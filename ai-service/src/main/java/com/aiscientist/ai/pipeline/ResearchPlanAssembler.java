package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;

import java.util.List;

/**
 * 10 字段《科学假设与研究计划》组装器。
 *
 * <p>从各阶段产物映射到赛题要求的标准化输出字段。
 * 尚未实现的阶段字段以「待生成」占位，接入后自动填充。</p>
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

        // 2. 解决思路（④ 假设生成）
        String rationale = PENDING;
        // 3. 技术手段（④ 假设生成）
        List<String> technicalDetails = List.of(PENDING);
        // 5. 标题 / 6. 摘要（③ 知识发现产出）
        String paperTitle = kd != null && hasText(kd.paperTitle()) ? kd.paperTitle() : PENDING;
        String paperAbstract = kd != null && hasText(kd.paperAbstract()) ? kd.paperAbstract() : PENDING;
        // 7. 方法论（④ 假设生成）
        List<String> methods = List.of(PENDING);
        // 9. 实验结果（⑥ 实验设计）
        String results = experiment != null && hasText(experiment.expectedResults())
                ? experiment.expectedResults()
                : PENDING;

        // 8. 实验设计（⑥ 实验设计）
        ResearchPlan.ExperimentPlan experimentPlan = experiment == null
                ? new ResearchPlan.ExperimentPlan(List.of(PENDING), List.of(PENDING))
                : new ResearchPlan.ExperimentPlan(experiment.baselines(), experiment.metrics());

        // 4. 数据集（数据引擎 + 评估）
        ResearchPlan.DatasetPlan datasetPlan = new ResearchPlan.DatasetPlan(
                List.of(PENDING), List.of(PENDING));

        // 10. 参考论文（⑤ 评估把关 / ③ 知识发现溯源），严禁虚构
        List<String> references = kd != null && !kd.references().isEmpty()
                ? kd.references()
                : List.of(PENDING);

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
}
