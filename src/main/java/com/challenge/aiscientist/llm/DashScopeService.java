package com.challenge.aiscientist.llm;

import com.challenge.aiscientist.config.DashScopeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class DashScopeService {
    private final DashScopeProperties properties;
    private final ApiCallLogService logService;

    public DashScopeService(DashScopeProperties properties, ApiCallLogService logService) {
        this.properties = properties;
        this.logService = logService;
    }

    public String chat(String prompt) {
        ensureConfigured();
        long started = System.nanoTime();
        try {
            JsonNode response = client().post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", properties.chatModel(), "messages", List.of(Map.of("role", "user", "content", prompt))))
                .retrieve().body(JsonNode.class);
            String answer = response.path("choices").path(0).path("message").path("content").asText();
            if (!StringUtils.hasText(answer)) throw new IllegalStateException("DashScope returned no assistant message");
            logService.add(new ApiCallLog(Instant.now(), "chat", properties.chatModel(), 200, elapsedMs(started), null));
            return answer;
        } catch (Exception exception) {
            logService.add(new ApiCallLog(Instant.now(), "chat", properties.chatModel(), 502, elapsedMs(started), exception.getClass().getSimpleName()));
            throw new ResponseStatusException(BAD_GATEWAY, "Qwen call failed; inspect the server log and DashScope configuration.");
        }
    }

    public List<Double> embed(String text) {
        ensureConfigured();
        long started = System.nanoTime();
        try {
            JsonNode response = client().post().uri("/embeddings").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", properties.embeddingModel(), "input", text)).retrieve().body(JsonNode.class);
            List<Double> vector = new ArrayList<>();
            for (JsonNode item : response.path("data").path(0).path("embedding")) {
                vector.add(item.asDouble());
            }
            if (vector.isEmpty()) throw new IllegalStateException("DashScope returned no embedding");
            logService.add(new ApiCallLog(Instant.now(), "embedding", properties.embeddingModel(), 200, elapsedMs(started), null));
            return vector;
        } catch (Exception exception) {
            logService.add(new ApiCallLog(Instant.now(), "embedding", properties.embeddingModel(), 502, elapsedMs(started), exception.getClass().getSimpleName()));
            throw new ResponseStatusException(BAD_GATEWAY, "Embedding call failed; inspect the server log and DashScope configuration.");
        }
    }

    private RestClient client() {
        return RestClient.builder().baseUrl(properties.baseUrl()).defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey()).build();
    }
    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.apiKey())) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "DASHSCOPE_API_KEY is not configured.");
    }
    private long elapsedMs(long started) { return (System.nanoTime() - started) / 1_000_000; }
}
