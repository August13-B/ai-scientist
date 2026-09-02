package com.aiscientist.ai.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * ④ 假设生成 Agent（骨架）。
 * 职责：利用归纳与演绎推理，综合文献检索与知识发现结果，生成 3–5 个候选科学假设，
 * 每个附带推理链条。输出：Rationale / Technical Details / Methods。
 * TODO（黄晴昀）：实现推理链条生成与候选假设输出 Schema。
 */
@SystemMessage("你是假设生成 Agent……")
public interface HypothesisGenerationAgent {
    // TODO: 定义假设生成方法签名（输出候选假设列表 + 推理链）
}
