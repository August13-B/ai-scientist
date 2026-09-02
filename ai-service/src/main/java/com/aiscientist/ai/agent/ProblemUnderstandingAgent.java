package com.aiscientist.ai.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * ① 问题理解 Agent（骨架）。
 * 职责：将用户输入的自然语言科研问题拆解为结构化子查询，
 * 识别领域标签、关键概念、已知条件与待求解变量。
 * 输入：科研问题描述；输出：子查询集合。
 * TODO（张睿/任怡名）：定义 @AiService 接口、System Prompt 与输出 Schema。
 */
@SystemMessage("你是问题理解 Agent……")
public interface ProblemUnderstandingAgent {
    // TODO: 定义拆解子查询的方法签名
}
