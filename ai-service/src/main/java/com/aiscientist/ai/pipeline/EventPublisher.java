package com.aiscientist.ai.pipeline;

/**
 * 管线事件发布接口（占位）。
 *
 * <p>对应 docs/architecture.md 的 SSE 事件模型：</p>
 * <ul>
 *   <li>{@code agent.start} / {@code agent.thinking} / {@code agent.result}</li>
 *   <li>{@code pipeline.pause}（人在回路暂停点） / {@code pipeline.resume}</li>
 *   <li>{@code pipeline.done} / {@code pipeline.error}</li>
 * </ul>
 *
 * <p>TODO（张睿 + 丁贾峻）：接入 SSE 推送（前端经业务后端转发），
 * PipelineEngine 在各阶段执行前后发布事件。</p>
 */
public interface EventPublisher {

    /**
     * 发布一个管线事件。
     *
     * @param taskId    任务标识
     * @param eventType 事件类型（agent.start 等）
     * @param data      事件负载（Agent 名称、阶段产物等）
     */
    void publish(String taskId, String eventType, Object data);

    /**
     * 管线结束：清理订阅、释放连接（默认空实现，SSE 发布器覆写）。
     *
     * @param taskId 任务标识
     */
    default void complete(String taskId) {
        // 默认无订阅需清理
    }
}
