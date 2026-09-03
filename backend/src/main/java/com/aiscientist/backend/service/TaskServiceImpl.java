package com.aiscientist.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务业务服务（纯转发网关实现）。
 *
 * <p>用内存 {@link ConcurrentHashMap}{@code <Long, String>} 维护 taskId ↔ runId，
 * 通过 {@link WebClient} 把请求转发到 ai-service（ai-service.base-url）。
 * 不做 MySQL 持久化，符合「纯转发网关」定位。</p>
 */
@Service
public class TaskServiceImpl implements TaskService {

    private final WebClient webClient;
    private final ConcurrentHashMap<Long, String> taskToRunId = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1_000);

    public TaskServiceImpl(
            @Value("${ai-service.base-url:http://localhost:8081}") String aiServiceBaseUrl,
            WebClient.Builder webClientBuilder
    ) {
        this.webClient = webClientBuilder.baseUrl(aiServiceBaseUrl).build();
    }

    @Override
    public Long createTask(String question) {
        Long taskId = idSequence.getAndIncrement();
        String runId = webClient.post()
                .uri("/pipeline/run")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("question", question == null ? "" : question))
                .retrieve()
                .bodyToMono(Map.class)
                .map(body -> String.valueOf(body.get("runId")))
                .block();
        if (runId == null || runId.isBlank()) {
            throw new IllegalStateException("ai-service 启动管线未返回 runId");
        }
        taskToRunId.put(taskId, runId);
        return taskId;
    }

    @Override
    public Map<String, Object> getTask(Long taskId) {
        return webClient.get()
                .uri("/pipeline/{runId}/state", requireRunId(taskId))
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class<?>) Map.class)
                .block();
    }

    @Override
    public Map<String, Object> getReport(Long taskId) {
        Map<String, Object> state = getTask(taskId);
        return Map.of("report", state == null ? null : state.get("finalReport"));
    }

    @Override
    public List<Map<String, Object>> listRuns() {
        return webClient.get()
                .uri("/pipeline/runs")
                .retrieve()
                .bodyToMono((Class<List<Map<String, Object>>>) (Class<?>) List.class)
                .block();
    }

    @Override
    public List<Map<String, Object>> trace(Long taskId) {
        return webClient.get()
                .uri("/pipeline/{runId}/trace", requireRunId(taskId))
                .retrieve()
                .bodyToMono((Class<List<Map<String, Object>>>) (Class<?>) List.class)
                .block();
    }

    @Override
    public SseEmitter stream(Long taskId) {
        String runId = requireRunId(taskId);
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        // 从 ai-service 读 SSE 事件流，原样转发到前端 emitter
        Disposable subscription = webClient.get()
                .uri("/pipeline/{runId}/stream", runId)
                .retrieve()
                .bodyToFlux(org.springframework.http.codec.ServerSentEvent.class)
                .subscribe(event -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name(event.event())
                                .data(event.data()));
                    } catch (Exception e) {
                        // 前端断开 / emitter 已关闭：取消订阅
                    }
                }, error -> emitter.complete(), emitter::complete);
        emitter.onCompletion(() -> subscription.dispose());
        emitter.onTimeout(() -> subscription.dispose());
        emitter.onError(e -> subscription.dispose());
        return emitter;
    }

    @Override
    public Map<String, Object> intervene(Long taskId, Map<String, Object> feedback) {
        String runId = requireRunId(taskId);
        Map<String, Object> resumeBody = Map.of(
                "reviewComment", feedback == null ? null : feedback.getOrDefault("reviewComment", ""),
                "revisedHypotheses", feedback == null ? List.of() : feedback.getOrDefault("revisedHypotheses", List.of())
        );
        return webClient.post()
                .uri("/pipeline/{runId}/resume", runId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(resumeBody)
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class<?>) Map.class)
                .block();
    }

    private String requireRunId(Long taskId) {
        String runId = taskToRunId.get(taskId);
        if (runId == null) {
            throw new IllegalArgumentException("未找到任务对应的管线 runId，taskId=" + taskId);
        }
        return runId;
    }
}
