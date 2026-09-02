package com.aiscientist.ai.rag;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import com.aiscientist.ai.llm.BailianClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 四库 RAG 检索服务（Chroma REST 实现）。
 *
 * <p>检索链路：query 文本 → 百炼 embedding（text-embedding-v3）→ Chroma
 * {@code POST /api/v1/collections/{name}/query} 向量检索 → 结果映射回
 * {@link PaperEvidence}（title/content/doi/pmid/url/authors/year，与灌库脚本
 * metadata 契约对齐，见 docs/rag.md 第 5 节）。</p>
 *
 * <p>collection 命名与灌库脚本一致：papers / methods / datasets / evidence。
 * 来源标识规范：{@code source_id} = {@code doi:xxx} / {@code pmid:xxx} / {@code url:xxx}。</p>
 *
 * <p>Milvus：生产环境切换 {@code VECTOR_DB=milvus} 时暂未接入（TODO 丁贾峻），
 * 调用会抛出明确提示而非静默返回空。</p>
 */
@Service
public class RagSearchService {

    private static final String EMBEDDING_MODEL = "text-embedding-v3";
    private static final String QUERY_PATH = "/api/v1/collections/%s/query";

    private final String vectorDb;
    private final String chromaBaseUrl;
    private final Duration timeout;
    private final BailianClient bailianClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RagSearchService(
            @Value("${vector.db:chroma}") String vectorDb,
            @Value("${vector.chroma.host:localhost}") String chromaHost,
            @Value("${vector.chroma.port:8000}") int chromaPort,
            @Value("${BAILIAN_TIMEOUT_SECONDS:60}") long timeoutSeconds,
            BailianClient bailianClient
    ) {
        this.vectorDb = vectorDb == null ? "chroma" : vectorDb.trim().toLowerCase();
        this.chromaBaseUrl = "http://" + chromaHost + ":" + chromaPort;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.bailianClient = bailianClient;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 在指定知识库检索与 query 语义最接近的 Top-K 条目。
     *
     * @param knowledgeBase 知识库名（papers / methods / datasets / evidence）
     * @param query         检索文本
     * @param topK          返回条数
     * @return 检索结果（字段与 PaperEvidence 对齐，携带来源标识）
     */
    public List<PaperEvidence> search(String knowledgeBase, String query, int topK) {
        if ("milvus".equals(vectorDb)) {
            throw new UnsupportedOperationException(
                    "VECTOR_DB=milvus 暂未接入（TODO 丁贾峻）：请先用 chroma 或实现 Milvus SDK 检索");
        }
        if (!"chroma".equals(vectorDb)) {
            throw new IllegalStateException("未知 VECTOR_DB=" + vectorDb + "（支持 chroma | milvus）");
        }
        List<List<Double>> embeddings = bailianClient.embed(List.of(query));
        if (embeddings.isEmpty()) {
            return List.of();
        }
        return queryChroma(knowledgeBase, embeddings.get(0), topK);
    }

    // ==================== 内部实现 ====================

    private List<PaperEvidence> queryChroma(String collection, List<Double> queryVector, int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(queryVector));
        body.put("n_results", topK);

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chromaBaseUrl + String.format(QUERY_PATH, collection)))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Chroma 检索失败 HTTP " + response.statusCode()
                        + "：" + abbreviate(response.body()));
            }
            return toPaperEvidence(objectMapper.readTree(response.body()));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Chroma 检索失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * Chroma query 响应 → PaperEvidence 列表。
     * 响应结构：{ids:[["id"]], metadatas:[[{source_id,title,year,venue,authors}]],
     * documents:[["正文"]], distances:[[score]]}
     */
    List<PaperEvidence> toPaperEvidence(JsonNode collection) {
        JsonNode ids = collection.path("ids");
        JsonNode metadatas = collection.path("metadatas");
        JsonNode documents = collection.path("documents");
        int size = ids.isArray() && !ids.isEmpty() ? ids.get(0).size() : 0;

        List<PaperEvidence> papers = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            JsonNode metadata = metadatas.path(0).path(i);
            String sourceId = metadata.path("source_id").asText("");
            String title = metadata.path("title").asText("");
            String content = documents.path(0).path(i).asText("");
            String authors = metadata.path("authors").asText("");
            int year = metadata.path("year").isInt() ? metadata.path("year").asInt() : 0;
            papers.add(new PaperEvidence(
                    title,
                    content,
                    splitAuthors(authors),
                    year == 0 ? null : year,
                    doiOf(sourceId),
                    pmidOf(sourceId),
                    urlOf(sourceId)
            ));
        }
        return List.copyOf(papers);
    }

    /** source_id（doi:xxx / pmid:xxx / url:xxx）→ 原始 doi，其余返回 null */
    static String doiOf(String sourceId) {
        return sourceId.startsWith("doi:") ? sourceId.substring(4) : null;
    }

    /** source_id → 原始 pmid，其余返回 null */
    static String pmidOf(String sourceId) {
        return sourceId.startsWith("pmid:") ? sourceId.substring(5) : null;
    }

    /** source_id → 原始 url，其余返回 null */
    static String urlOf(String sourceId) {
        return sourceId.startsWith("url:") ? sourceId.substring(4) : null;
    }

    static List<String> splitAuthors(String authors) {
        if (authors == null || authors.isBlank()) {
            return List.of();
        }
        String[] parts = authors.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                result.add(part.trim());
            }
        }
        return List.copyOf(result);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
