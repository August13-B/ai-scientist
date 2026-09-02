package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.rag.RagSearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管线 HTTP 接口（ai-service 8081，供 backend 调用/转发）。
 *
 * <p>对应 docs/api-design.md 内部接口清单：</p>
 * <ul>
 *   <li>{@code POST /pipeline/run} 启动管线，立即返回 runId（异步执行）</li>
 *   <li>{@code GET /pipeline/{runId}/stream} SSE 事件流（backend 转发给前端）</li>
 *   <li>{@code POST /pipeline/{runId}/resume} 人在回路恢复（提交审阅意见）</li>
 *   <li>{@code GET /pipeline/{runId}/state} 查询当前管线状态</li>
 *   <li>{@code POST /rag/search} 四库混合检索（Agent 内部 / 直连接口）</li>
 * </ul>
 */
@RestController
@RequestMapping("/pipeline")
public class PipelineController {

    private final PipelineEngine engine;
    private final RagSearchService ragSearchService;

    public PipelineController(PipelineEngine engine, RagSearchService ragSearchService) {
        this.engine = engine;
        this.ragSearchService = ragSearchService;
    }

    /** 启动管线请求体 */
    public record RunRequest(String question) {
    }

    /** 启动管线响应 */
    public record RunResponse(String runId) {
    }

    /** 启动管线：立即返回 runId，后台异步执行 */
    @PostMapping("/run")
    public RunResponse run(@RequestBody RunRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return new RunResponse(engine.start(request.question()));
    }

    /** SSE 事件流：订阅 runId 的 Agent 状态事件（含历史重放） */
    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId) {
        SseEmitter emitter = new SseEmitter(TimeUnit.MINUTES.toMillis(30));
        engine.registerStream(runId, emitter);
        return emitter;
    }

    /** 人在回路恢复：提交人类审阅意见/修改后的候选假设 */
    @PostMapping("/{runId}/resume")
    public Map<String, String> resume(
            @PathVariable String runId,
            @RequestBody(required = false) PipelineModels.HumanFeedback feedback
    ) {
        engine.resume(runId, feedback == null ? new PipelineModels.HumanFeedback(null, List.of()) : feedback);
        return Map.of("status", "resumed", "runId", runId);
    }

    /** 查询管线当前状态（各阶段产物快照） */
    @GetMapping("/{runId}/state")
    public PipelineContext state(@PathVariable String runId) {
        return engine.state(runId);
    }

    /** 四库 RAG 检索（papers / methods / datasets / evidence） */
    @PostMapping(value = "/rag/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence> ragSearch(
            @RequestBody RagSearchRequest request
    ) {
        return ragSearchService.search(request.knowledgeBase(), request.query(), request.topK());
    }

    /** RAG 检索请求体 */
    public record RagSearchRequest(String knowledgeBase, String query, int topK) {
        public RagSearchRequest {
            if (knowledgeBase == null || knowledgeBase.isBlank()) {
                throw new IllegalArgumentException("knowledgeBase must not be blank");
            }
            if (query == null || query.isBlank()) {
                throw new IllegalArgumentException("query must not be blank");
            }
            if (topK <= 0 || topK > 100) {
                throw new IllegalArgumentException("topK must be between 1 and 100");
            }
        }
    }
}
