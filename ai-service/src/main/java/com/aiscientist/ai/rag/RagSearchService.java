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
 *
 * <p><b>本地调试模式</b>：{@code vector.mock-samples=true}（.env {@code RAG_MOCK_SAMPLES=true}）
 * 时不连向量库，任何检索都返回内置样例论文（{@link #MOCK_SAMPLES}，DOI 均为
 * Crossref 真实存在的论文，保证 ⑤ 引用核验可过），用于无 Chroma/无灌库数据时
 * 本地跑通 ②③④ 全链路（开关默认关闭）。</p>
 */
@Service
public class RagSearchService {

    private static final String EMBEDDING_MODEL = "text-embedding-v3";
    private static final String QUERY_PATH = "/api/v1/collections/%s/query";

    /** 本地调试样例论文（水稻病害检测方向，DOI 均经 Crossref 实测 HTTP 200） */
    private static final List<PaperEvidence> MOCK_SAMPLES = List.of(
            new PaperEvidence(
                    "Advances in Rice Plant Disease Detection: A Survey of Machine Learning and Deep Learning",
                    "对水稻病害检测的机器学习/深度学习方法综述：CNN 用于稻瘟病、白叶枯病识别，"
                            + "指出田间复杂背景与小样本是主要挑战，提出数据增强与迁移学习改进方向。",
                    List.of("Anandhi", "Sathyavathi"), 2023,
                    "10.21275/sr231218142714", null, null),
            new PaperEvidence(
                    "A Review of Rice Blast Disease Detection Using Machine Learning and Deep Learning",
                    "系统回顾稻瘟病检测的机器学习与深度学习方法，比较传统特征工程与 CNN 端到端学习"
                            + "在病害分类上的精度，讨论数据集规模与标注质量对模型泛化的影响。",
                    List.of("Saha", "Thakur"), 2024,
                    "10.2139/ssrn.4889598", null, null),
            new PaperEvidence(
                    "Rice Plant Disease Detection and Classification Framework Using Deep Learning for Precision Agriculture",
                    "提出面向精准农业的水稻植株病害检测与分类框架，融合图像预处理与 CNN 分类器，"
                            + "在田间图像上评估，强调光照与拍摄角度变化对识别鲁棒性的影响。",
                    List.of("JCR Authors"), 2023,
                    "10.48047/jcr.07.09.577", null, null),
            new PaperEvidence(
                    "A Deep Learning Approach for Automated Rice Disease Detection and Classification",
                    "提出水稻病害自动检测分类的深度学习方法，在公开数据集上比较多种 CNN 架构，"
                            + "分析小样本与类别不平衡条件下的性能退化与应对策略。",
                    List.of("Mandwariya", "Jotwani"), 2024,
                    "10.53555/jaz.v45i3.4313", null, null)
    );

    private final String vectorDb;
    private final String chromaBaseUrl;
    private final boolean mockSamples;
    private final Duration timeout;
    private final BailianClient bailianClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RagSearchService(
            @Value("${vector.db:chroma}") String vectorDb,
            @Value("${vector.chroma.host:localhost}") String chromaHost,
            @Value("${vector.chroma.port:8000}") int chromaPort,
            @Value("${BAILIAN_TIMEOUT_SECONDS:60}") long timeoutSeconds,
            @Value("${vector.mock-samples:false}") boolean mockSamples,
            BailianClient bailianClient
    ) {
        this.vectorDb = vectorDb == null ? "chroma" : vectorDb.trim().toLowerCase();
        this.chromaBaseUrl = "http://" + chromaHost + ":" + chromaPort;
        this.mockSamples = mockSamples;
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
        // 本地调试模式：返回内置样例论文，跳过向量库（不连 Chroma/无灌库数据时全链路可跑）
        if (mockSamples) {
            int limit = Math.max(1, Math.min(topK, MOCK_SAMPLES.size()));
            return List.copyOf(MOCK_SAMPLES.subList(0, limit));
        }
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
