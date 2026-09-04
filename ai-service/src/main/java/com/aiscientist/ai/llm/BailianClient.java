package com.aiscientist.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阿里云百炼平台 Qwen 调用封装（OpenAI 兼容 API）。
 *
 * <p>职责：统一封装百炼 {@code https://dashscope.aliyuncs.com/compatible-mode/v1}
 * 的 chat/completions 与 embeddings 端点；记录调用日志；模型分级路由。</p>
 *
 * <p><b>模型分级路由</b>：调用方仍按语义传模型标识（重任务 {@code qwen-max}、
 * 轻任务 {@code qwen-plus}、极轻 {@code qwen-turbo}），本类将其映射到
 * 环境变量配置的真实模型名（{@code QWEN_MODEL / QWEN_LIGHT_MODEL / QWEN_TURBO_MODEL}），
 * 便于部署时统一切换而不改调用方代码。</p>
 *
 * <p>重试：网络/5xx 异常自动重试 1 次；超时 60s（可配 {@code BAILIAN_TIMEOUT_SECONDS}）。</p>
 *
 * <p>TODO（丁贾峻）：{@link #streamChat(String, String, String)} 流式调用实现
 * （SSE token 流，用于前端 agent.thinking 事件）。</p>
 */
@Component
public class BailianClient {

    /** 模型别名映射：调用方语义名 → 配置的真实模型名（构造时解析配置） */
    private final Map<String, String> modelAliases;

    private final String baseUrl;
    private final String embeddingModel;
    private final String apiKey;
    private final double temperature;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BailianClient(
            @Value("${langchain4j.open-ai.chat-model.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key:}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.temperature:0.7}") double temperature,
            @Value("${BAILIAN_TIMEOUT_SECONDS:60}") long timeoutSeconds,
            @Value("${QWEN_MODEL:qwen-max}") String heavyModel,
            @Value("${QWEN_LIGHT_MODEL:qwen-plus}") String lightModel,
            @Value("${QWEN_TURBO_MODEL:qwen-turbo}") String turboModel,
            @Value("${vector.embedding-model:text-embedding-v4}") String embeddingModel
    ) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.temperature = temperature;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.embeddingModel = embeddingModel;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
        this.objectMapper = new ObjectMapper();
        // 别名表在构造时解析配置占位符（部署可切真实模型名）
        this.modelAliases = Map.of(
                "qwen-max", heavyModel,
                "qwen-heavy", heavyModel,
                "qwen-plus", lightModel,
                "qwen-light", lightModel,
                "qwen-turbo", turboModel
        );
    }

    /**
     * 非流式对话补全：返回模型生成的完整文本（JSON 结构由调用方解析）。
     *
     * @param model        模型标识（支持别名 qwen-max/qwen-plus/qwen-turbo 及配置真实名）
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return 模型回复文本
     */
    public String chat(String model, String systemPrompt, String userMessage) {
        return chat(model, systemPrompt, userMessage, null);
    }

    /**
     * 非流式长文本对话补全，可为报告等重任务单独设置输出 token 上限。
     * 普通 Agent 继续使用三参数方法，不改变原有行为。
     */
    public String chat(String model, String systemPrompt, String userMessage,
                       Integer maxTokens) {
        requireApiKey();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", resolveModel(model));
        body.put("temperature", temperature);
        if (maxTokens != null && maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage)
        ));

        String response = postWithRetry("/chat/completions", body, 1);
        return parseChatContent(response);
    }

    /**
     * 文本向量化（embeddings 端点，供四库 RAG 检索对 query 编码）。
     *
     * @param texts 待向量化文本列表
     * @return 每个文本一个向量（维度与灌库脚本一致：text-embedding-v3，1024）
     */
    public List<List<Double>> embed(List<String> texts) {
        requireApiKey();
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embeddingModel);
        body.put("input", texts);

        String response = postWithRetry("/embeddings", body, 1);
        return parseEmbeddings(response);
    }

    /**
     * 流式对话补全（预留接口）。
     * <p>TODO（丁贾峻）：以 SSE 方式调用 chat/completions（stream=true），
     * 逐 token 回调（前端 agent.thinking 事件），返回完整文本。</p>
     */
    public String streamChat(String model, String systemPrompt, String userMessage) {
        throw new UnsupportedOperationException("streamChat 尚未实现（TODO 丁贾峻）");
    }

    // ==================== 内部实现 ====================

    private String postWithRetry(String path, Object body, int retries) {
        HttpRequest request = buildRequest(path, body);
        Exception lastError;
        int attempts = retries + 1;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return response.body();
                }
                if (status >= 500 && attempt < attempts) {
                    // 服务端异常：重试
                    continue;
                }
                throw new IllegalStateException("百炼 API 返回 HTTP " + status
                        + "：" + abbreviate(response.body()));
            } catch (IllegalStateException exception) {
                throw exception;
            } catch (Exception exception) {
                lastError = exception;
                if (attempt < attempts) {
                    continue;
                }
                throw new IllegalStateException("百炼 API 调用失败：" + exception.getMessage(),
                        exception);
            }
        }
        // 不可达（循环至少执行一次）
        throw new IllegalStateException("百炼 API 调用失败");
    }

    private HttpRequest buildRequest(String path, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            return HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException("构建百炼 API 请求失败", exception);
        }
    }

    /** 解析 chat/completions 响应中的 choices[0].message.content */
    static String parseChatContent(String responseJson) {
        try {
            JsonNode root = new ObjectMapper().readTree(responseJson);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new IllegalArgumentException("百炼响应缺少 choices[0].message.content");
            }
            return content.asText();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("百炼响应不是合法 JSON", exception);
        }
    }

    /** 解析 embeddings 响应中的 data[].embedding */
    static List<List<Double>> parseEmbeddings(String responseJson) {
        try {
            JsonNode data = new ObjectMapper().readTree(responseJson).path("data");
            List<List<Double>> vectors = new java.util.ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray()) {
                    throw new IllegalArgumentException("embeddings 响应缺少数组字段 embedding");
                }
                List<Double> vector = new java.util.ArrayList<>();
                embedding.forEach(value -> vector.add(value.asDouble()));
                vectors.add(List.copyOf(vector));
            }
            return List.copyOf(vectors);
        } catch (Exception exception) {
            throw new IllegalArgumentException("百炼 embeddings 响应不是合法 JSON", exception);
        }
    }

    /** 模型别名 → 配置真实模型名；未知标识原样透传 */
    String resolveModel(String model) {
        String resolved = modelAliases.get(model);
        return resolved == null ? model : resolved;
    }

    private void requireApiKey() {
        if (apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "缺少 ALIYUN_BAILIAN_API_KEY，请在 .env 配置后启动 ai-service");
        }
    }

    private static String trimTrailingSlash(String url) {
        return url == null ? "" : url.replaceAll("/+$", "");
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
