package com.aiscientist.backend.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 任务业务服务（纯转发网关实现契约）。
 *
 * <p>职责：把前端（经 backend）的请求转发到 ai-service 的管线接口，
 * 并维护 taskId ↔ runId 的映射（内存，不持久化）。不承担 MySQL 持久化。</p>
 *
 * <p>对应 ai-service 接口（经 ai-service.base-url 转发）：</p>
 * <ul>
 *   <li>{@code POST /pipeline/run} → createTask 启动管线</li>
 *   <li>{@code GET /pipeline/{runId}/stream} → stream 转发 SSE 事件</li>
 *   <li>{@code POST /pipeline/{runId}/resume} → intervene 人在回路</li>
 *   <li>{@code GET /pipeline/{runId}/state} → getTask / getReport</li>
 *   <li>{@code GET /pipeline/{runId}/trace} → trace Agent 级输入输出</li>
 *   <li>{@code GET /pipeline/runs} → runs 列表</li>
 * </ul>
 */
public interface TaskService {

    /** 创建任务：转发 ai-service 启动管线，返回 taskId（前端标识）。 */
    Long createTask(String question);

    /** 查询任务（转发 ai-service state 快照）。 */
    Map<String, Object> getTask(Long taskId);

    /** 获取生成的研究计划（转发 ai-service state.finalReport）。 */
    Map<String, Object> getReport(Long taskId);

    /** 全部已启动的 run 列表（转发 ai-service /pipeline/runs）。 */
    List<Map<String, Object>> listRuns();

    /** Agent 级执行追踪（转发 ai-service /pipeline/{runId}/trace）。 */
    List<Map<String, Object>> trace(Long taskId);

    /** SSE 流：转发 ai-service /pipeline/{runId}/stream 的事件到前端 emitter。 */
    SseEmitter stream(Long taskId);

    /** 人在回路：转发审阅意见到 ai-service resume，恢复管线。 */
    Map<String, Object> intervene(Long taskId, Map<String, Object> feedback);
}
