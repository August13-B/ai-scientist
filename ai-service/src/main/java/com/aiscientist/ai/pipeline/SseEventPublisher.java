package com.aiscientist.ai.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 管线事件发布器（SSE 实现）。
 *
 * <p>对应 docs/architecture.md 的 SSE 事件模型：{@code agent.start} /
 * {@code agent.thinking} / {@code agent.result} / {@code pipeline.pause} /
 * {@code pipeline.resume} / {@code pipeline.done} / {@code pipeline.error}。</p>
 *
 * <p>特性：</p>
 * <ul>
 *   <li>每个 runId 维护 {@link SseEmitter} 与事件历史（{@link EventRecord}），
 *       断线重连或晚订阅时重放历史，保证事件不丢；</li>
 *   <li>{@link #complete(String)} 在管线结束时清理订阅，释放连接。</li>
 * </ul>
 *
 * <p>SSE 链路：前端 → backend 转发 → ai-service（本发布器）。backend 转发由后端组实现。</p>
 */
@Component
public class SseEventPublisher implements EventPublisher {

    /** 事件记录（历史重放用） */
    public record EventRecord(String eventType, String data) {
    }

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<EventRecord>> history = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEventPublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(String taskId, String eventType, Object data) {
        String payload = toJson(data);
        history.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>())
                .add(new EventRecord(eventType, payload));
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            send(emitter, eventType, payload);
        }
    }

    /**
     * 注册 runId 的 SSE 订阅（GET /pipeline/{runId}/stream）。
     * 注册后立即重放该 runId 已发生的历史事件。
     */
    public void register(String taskId, SseEmitter emitter) {
        emitters.put(taskId, emitter);
        emitter.onCompletion(() -> emitters.remove(taskId, emitter));
        emitter.onTimeout(() -> emitters.remove(taskId, emitter));
        emitter.onError(error -> emitters.remove(taskId, emitter));

        List<EventRecord> records = history.getOrDefault(taskId, new CopyOnWriteArrayList<>());
        for (EventRecord record : records) {
            send(emitter, record.eventType(), record.data());
        }
    }

    /** 管线结束：清理订阅（保留历史供状态查询） */
    public void complete(String taskId) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            emitter.complete();
        }
    }

    // ==================== 内部实现 ====================

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception exception) {
            return "{\"error\":\"事件序列化失败\"}";
        }
    }

    private void send(SseEmitter emitter, String eventType, String payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(payload));
        } catch (IOException | IllegalStateException exception) {
            emitters.remove(emitter);
        }
    }
}
