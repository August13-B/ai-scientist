package com.aiscientist.ai.pipeline;

/**
 * 管线阶段枚举（对应 docs/agents.md 七 Agent 编排顺序）。
 * 编号即执行顺序；LITERATURE / KNOWLEDGE / HYPOTHESIS 为并行阶段。
 */
public enum AgentStage {

    /** ① 问题理解：拆解为结构化子查询 */
    UNDERSTANDING(1),

    /** ② 文献检索：论文库/证据库召回（并行） */
    LITERATURE(2),

    /** ③ 知识发现：Research Gap 排序（并行） */
    KNOWLEDGE(3),

    /** ④ 假设生成：候选假设 + 推理链（并行） */
    HYPOTHESIS(4),

    /** ⑤ 科学假设评估：多维度评分 + 幻觉检测 */
    EVALUATION(5),

    /** ⑥ 实验设计：Baselines + Metrics + 数据集 */
    EXPERIMENT(6),

    /** ⑦ 思辨辩论：倡议者 vs 质疑者 */
    DEBATE(7);

    private final int order;

    AgentStage(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }
}
