package com.aiscientist.ai.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * ③ 知识发现 Agent（骨架）。
 * 职责：基于论文库与方法库进行跨文献知识关联挖掘，识别研究空白（Research Gap）
 * 与技术迁移机会。输出：Problem Statement / Paper Title / Paper Abstract。
 * TODO（马艺萌）：实现跨文献关联挖掘逻辑与输出 Schema。
 */
@SystemMessage("你是知识发现 Agent……")
public interface KnowledgeDiscoveryAgent {
    // TODO: 定义知识发现方法签名（输出 Problem Statement / Title / Abstract）
}
