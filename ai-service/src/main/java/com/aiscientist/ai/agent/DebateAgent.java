package com.aiscientist.ai.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * ⑦ 思辨辩论 Agent（骨架）。
 * 职责：「倡议者」与「质疑者」两个子 Agent 进行结构化辩论，多轮迭代完善研究计划。
 * 人机协作：前端展示辩论过程，人类导师可参与。
 * TODO（钱思妤 + 吴浩瑜前端配合）：实现双角色辩论循环与迭代收敛逻辑。
 */
@SystemMessage("你是思辨辩论 Agent……")
public interface DebateAgent {
    // TODO: 定义辩论方法签名（输出辩论纪要 + 完善后的研究计划）
}
