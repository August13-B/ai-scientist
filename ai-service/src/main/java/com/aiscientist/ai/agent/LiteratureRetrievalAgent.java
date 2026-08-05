package com.aiscientist.ai.agent;

import dev.langchain4j.service.SystemMessage;

/**
 * ② 文献检索 Agent（骨架）。
 * 职责：基于子查询在论文库与证据库中进行向量检索，召回 Top-K 相关文献，
 * 提取关键段落与引用链。
 * TODO（张睿）：接入 rag 包检索接口，定义召回与引用链提取逻辑。
 */
@SystemMessage("你是文献检索 Agent……")
public interface LiteratureRetrievalAgent {
    // TODO: 定义检索方法签名（输入子查询，输出文献列表 + 引用链）
}
