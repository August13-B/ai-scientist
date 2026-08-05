package com.aiscientist.ai.pipeline;

import org.springframework.stereotype.Component;

/**
 * 管线编排引擎（骨架）。
 * 职责：以 DAG 逻辑串联七 Agent，管理 State 状态流转，支持并行执行、
 * 人在回路暂停/恢复、异常重试与断点恢复。
 *
 * 状态机：IDLE → UNDERSTANDING → {RETRIEVING, KNOWLEDGE, HYPOTHESIS}(并行)
 *        → AGGREGATED → WAITING_HUMAN(人在回路) → EVALUATING → DESIGNING
 *        → DEBATING → DONE / ERROR
 *
 * TODO（张睿）：基于 LangChain4j 实现 DAG 编排与 State 管理。
 */
@Component
public class PipelineEngine {

    public void run(String taskId, String question) {
        // TODO: 启动七 Agent 管线，按 DAG 流转状态，SSE 推送事件
    }

    public void resume(String taskId) {
        // TODO: 人在回路恢复：接收人类审阅意见后继续执行
    }
}
