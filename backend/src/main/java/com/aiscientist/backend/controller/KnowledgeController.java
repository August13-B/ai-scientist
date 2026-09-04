package com.aiscientist.backend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * RAG 四库网关：把前端统计和检索请求转发给 ai-service。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final WebClient webClient;

    public KnowledgeController(
            @Value("${ai-service.base-url:http://localhost:8081}") String aiServiceBaseUrl,
            WebClient.Builder webClientBuilder
    ) {
        this.webClient = webClientBuilder.baseUrl(aiServiceBaseUrl).build();
    }

    /** 返回四库数据量、向量模型与运行模式。 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return webClient.get()
                .uri("/pipeline/rag/stats")
                .retrieve()
                .bodyToMono((Class<Map<String, Object>>) (Class<?>) Map.class)
                .block();
    }

    /** 执行精选来源与上传全文分块的混合向量检索。 */
    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> search(@RequestBody Map<String, Object> request) {
        return webClient.post()
                .uri("/pipeline/rag/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request == null ? Map.of() : request)
                .retrieve()
                .bodyToMono((Class<List<Map<String, Object>>>) (Class<?>) List.class)
                .block();
    }
}
