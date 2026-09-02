package com.aiscientist.ai.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * ⑤ 科学假设评估 Agent（骨架）。
 * 职责：多维度评分（创新性、可行性、引用真实性、数据可获得性）；
 * 内置幻觉检测——反向比对真实文献，虚构引用立即打回重做。
 * 输出：评分排序 + 幻觉检测报告 / References（严禁虚构）。
 * TODO（钱思妤）：实现评分逻辑与幻觉检测（核心质量关卡）。
 */
@SystemMessage("你是科学假设评估 Agent，必须严格核验引用的真实性……")
public interface HypothesisEvaluationAgent {
    // TODO: 定义评估方法签名（输出评分、幻觉检测结果、真实 References）
}
