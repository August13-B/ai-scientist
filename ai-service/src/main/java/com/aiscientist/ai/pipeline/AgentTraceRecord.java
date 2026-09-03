package com.aiscientist.ai.pipeline;

import java.util.Map;

/**
 * 单次 Agent 执行追踪记录（调试/可视化用，前端可消费的 trace 规范）。
 *
 * <p>每次 {@link PipelineAgent} 执行时由 {@link PipelineEngine} 自动记录：</p>
 * <ul>
 *   <li>{@code input}：执行前按 {@link AgentStage} 数据契约从 ctx 取的输入字段快照
 *       （如 ④ 假设生成 = knowledgeDiscovery + literature）；</li>
 *   <li>{@code output}：执行后写入 ctx 的阶段产物（未产出/失败为 null）；</li>
 *   <li>{@code status}：SUCCESS / FAILED（FAILED 时 errorMessage 为错误消息）。</li>
 * </ul>
 *
 * <p>消费方式：{@code GET /pipeline/{runId}/trace}（JSON 全量）、
 * {@code GET /pipeline/{runId}/debug}（内嵌 HTML 调试页）、SSE agent.start/result 事件。</p>
 */
public record AgentTraceRecord(
        /** 阶段名（AgentStage.name()） */
        String stage,
        /** Agent 类名 */
        String agent,
        /** 开始时间（epoch millis） */
        long startTimeMillis,
        /** 执行耗时（毫秒） */
        long durationMillis,
        /** SUCCESS / FAILED */
        String status,
        /** 失败时的错误消息（成功为 null） */
        String errorMessage,
        /** 执行前输入字段快照（按阶段契约，不含 null 字段） */
        Map<String, Object> input,
        /** 执行后的阶段产物（失败/未产出为 null） */
        Object output
) {
}
