package com.aiscientist.ai.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * ⑥ 实验设计 Agent（骨架）。
 * 职责：为最优假设设计完整实验方案：Baselines、Metrics、拟用数据集、预期结果范围。
 * 输出：Experiments / Results。
 * TODO（王婉莹）：实现实验方案生成（含基线与指标）。
 */
@SystemMessage("你是实验设计 Agent……")
public interface ExperimentDesignAgent {
    // TODO: 定义实验设计方法签名（输出 Experiments / Results）
}
