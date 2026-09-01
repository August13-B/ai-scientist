package com.aiscientist.ai.pipeline;

import java.util.List;

/**
 * 最终输出：《科学假设与研究计划》10 字段（对应赛题生成结果规范）。
 * 字段序号对应 docs/agents.md 第 5 节。
 */
public record ResearchPlan(
        /** 1. 待研究问题（Problem Statement） */
        String problemStatement,
        /** 2. 解决思路（Rationale） */
        String rationale,
        /** 3. 必要的技术手段（Technical Details） */
        List<String> technicalDetails,
        /** 4. 数据集（Datasets：Source 历史数据 + Target 拟采集数据） */
        DatasetPlan datasets,
        /** 5. 标题（Paper Title） */
        String paperTitle,
        /** 6. 摘要（Paper Abstract） */
        String paperAbstract,
        /** 7. 方法论（Methods） */
        List<String> methods,
        /** 8. 实验设计（Experiments：Baselines + Metrics） */
        ExperimentPlan experiments,
        /** 9. 实验结果（Results） */
        String results,
        /** 10. 参考论文（References，真实文献，严禁虚构） */
        List<String> references
) {

    public ResearchPlan {
        problemStatement = PipelineModels.requireText(problemStatement, "problemStatement");
        technicalDetails = PipelineModels.immutable(technicalDetails);
        methods = PipelineModels.immutable(methods);
        references = PipelineModels.immutable(references);
        if (references.isEmpty()) {
            throw new IllegalArgumentException("references must not be empty");
        }
    }

    /** 数据集计划：Source（假设推演依据的历史数据）+ Target（验证实验拟采集数据） */
    public record DatasetPlan(List<String> source, List<String> target) {
        public DatasetPlan {
            source = PipelineModels.immutable(source);
            target = PipelineModels.immutable(target);
        }
    }

    /** 实验设计：Baselines 对比 + Metrics 评估指标 */
    public record ExperimentPlan(List<String> baselines, List<String> metrics) {
        public ExperimentPlan {
            baselines = PipelineModels.immutable(baselines);
            metrics = PipelineModels.immutable(metrics);
        }
    }
}
