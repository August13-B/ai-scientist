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
import java.util.Optional;

/**
 * 四库 RAG 检索服务（Chroma REST 实现）。
 *
 * <p>检索链路：query 文本 → 百炼 embedding（text-embedding-v4）→ Chroma
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

    private static final String EMBEDDING_MODEL = "text-embedding-v4";
    /** Chroma v2 端点：多租户/数据库结构（默认 default_tenant/default_database），路径段用 collection id(UUID) */
    private static final String COLLECTIONS_PATH =
            "/api/v2/tenants/default_tenant/databases/default_database/collections";
    private static final String QUERY_PATH = COLLECTIONS_PATH + "/%s/query";
    private static final String GET_PATH = COLLECTIONS_PATH + "/%s/get";
    private static final String COUNT_PATH = COLLECTIONS_PATH + "/%s/count";
    private static final List<String> KNOWLEDGE_BASES =
            List.of("papers", "methods", "datasets", "evidence");

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
        // 精选库与 *_vectors 都容忍缺失：哪个集合存在就用哪个，避免因灌库脚本不同
        // （ingest_vectors_chroma.py 建 papers/…；import_precomputed_vectors.py 建 papers_vectors/…）
        // 而抛“Chroma 未找到集合”。两者都无则返回空（由下游“至少两篇”报错兜底）。
        List<PaperEvidence> curated = queryChromaIfPresent(
                knowledgeBase, embeddings.get(0), topK);
        List<PaperEvidence> uploaded = queryChromaIfPresent(
                knowledgeBase + "_vectors", embeddings.get(0), topK);
        return hybridMerge(curated, uploaded, topK);
    }

    /**
     * 只检索带 DOI/HTTP URL 的人工精选集合。
     * 实验设计的数据集白名单必须使用该入口，避免把论文里的普通 workload 片段
     * 错当成可直接下载的数据集。
     */
    public List<PaperEvidence> searchCurated(String knowledgeBase, String query, int topK) {
        if (mockSamples) {
            int limit = Math.max(1, Math.min(topK, MOCK_SAMPLES.size()));
            return List.copyOf(MOCK_SAMPLES.subList(0, limit));
        }
        if (!"chroma".equals(vectorDb)) {
            if ("milvus".equals(vectorDb)) {
                throw new UnsupportedOperationException(
                        "VECTOR_DB=milvus 暂未接入（TODO 丁贾峻）：请先用 chroma 或实现 Milvus SDK 检索");
            }
            throw new IllegalStateException("未知 VECTOR_DB=" + vectorDb + "（支持 chroma | milvus）");
        }
        List<List<Double>> embeddings = bailianClient.embed(List.of(query));
        return embeddings.isEmpty() ? List.of()
                // 与 search() 一致容忍缺失：数据集库可能仅存在 *_vectors 形式
                : queryChromaIfPresent(knowledgeBase, embeddings.get(0), topK);
    }

    /**
     * 返回四库的真实 Chroma 数据量，供知识库监测页面展示。
     * curated 是带公开 DOI/PMID/URL 的精选条目，uploaded 是用户上传的全文向量分块。
     */
    public Map<String, Object> stats() {
        if (!"chroma".equals(vectorDb)) {
            throw new UnsupportedOperationException("知识库统计当前仅支持 Chroma");
        }

        Map<String, Object> libraries = new LinkedHashMap<>();
        int grandTotal = 0;
        for (String knowledgeBase : KNOWLEDGE_BASES) {
            int curated = countCollectionIfPresent(knowledgeBase);
            int uploaded = countCollectionIfPresent(knowledgeBase + "_vectors");
            int total = curated + uploaded;
            grandTotal += total;
            libraries.put(knowledgeBase, Map.of(
                    "curated", curated,
                    "uploaded", uploaded,
                    "total", total
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ready");
        result.put("mode", mockSamples ? "mock" : "production");
        result.put("vectorDatabase", "Chroma");
        result.put("embeddingModel", EMBEDDING_MODEL);
        result.put("dimensions", 1024);
        result.put("total", grandTotal);
        result.put("libraries", libraries);
        return Map.copyOf(result);
    }

    /**
     * 按灌库时保存的 {@code source_id} 精确查找条目。
     *
     * <p>引用核验不能把 URL 当作自然语言做相似度检索，否则真实的官方论文页
     * 也可能因为向量召回不稳定而被误判。这里使用 Chroma metadata where 条件
     * 做确定性查询。</p>
     */
    public Optional<PaperEvidence> findBySourceId(String knowledgeBase, String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return Optional.empty();
        }
        if (mockSamples) {
            return MOCK_SAMPLES.stream()
                    .filter(paper -> sourceId.equalsIgnoreCase(paper.sourceId()))
                    .findFirst();
        }
        if (!"chroma".equals(vectorDb)) {
            return Optional.empty();
        }

        Optional<PaperEvidence> curated = findBySourceIdInCollection(knowledgeBase, sourceId);
        if (curated.isPresent()) {
            return curated;
        }
        return findBySourceIdInCollection(knowledgeBase + "_vectors", sourceId);
    }

    private Optional<PaperEvidence> findBySourceIdInCollection(String collection, String sourceId) {
        String collectionId = findCollectionId(collection);
        if (collectionId == null) {
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("where", Map.of("source_id", Map.of("$eq", sourceId)));
        body.put("limit", 1);
        body.put("include", List.of("metadatas", "documents"));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chromaBaseUrl + String.format(GET_PATH, collectionId)))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Chroma 精确查询失败 HTTP " + response.statusCode()
                        + "：" + abbreviate(response.body()));
            }
            return toSinglePaperEvidence(objectMapper.readTree(response.body()));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Chroma 精确查询失败：" + exception.getMessage(), exception);
        }
    }

    // ==================== 内部实现 ====================

    private List<PaperEvidence> queryChroma(String collection, List<Double> queryVector, int topK) {
        // Chroma v2：query 路径用 collection 的 id(UUID)，先按名字解析出 id
        String collectionId = resolveCollectionId(collection);
        return queryChromaById(collectionId, queryVector, topK);
    }

    private List<PaperEvidence> queryChromaIfPresent(
            String collection, List<Double> queryVector, int topK) {
        String collectionId = findCollectionId(collection);
        return collectionId == null ? List.of() : queryChromaById(collectionId, queryVector, topK);
    }

    private List<PaperEvidence> queryChromaById(
            String collectionId, List<Double> queryVector, int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(queryVector));
        body.put("n_results", topK);

        try {
            String requestJson = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chromaBaseUrl + String.format(QUERY_PATH, collectionId)))
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
     * 按 collection 名字解析其 id(UUID)。
     * Chroma v2 的 query 路径段要求 collection 的 id 而非 name。
     * GET /api/v2/tenants/{t}/databases/{d}/collections 返回 [{id,name,...}]。
     */
    String resolveCollectionId(String name) {
        String collectionId = findCollectionId(name);
        if (collectionId == null) {
            throw new IllegalStateException("Chroma 未找到集合：" + name + "（请先导入四库向量）");
        }
        return collectionId;
    }

    private String findCollectionId(String name) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chromaBaseUrl + COLLECTIONS_PATH))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Chroma 列出集合失败 HTTP " + response.statusCode());
            }
            JsonNode collections = objectMapper.readTree(response.body());
            if (!collections.isArray()) {
                throw new IllegalStateException("Chroma 集合列表响应不是数组");
            }
            for (JsonNode item : collections) {
                if (name.equals(item.path("name").asText())) {
                    return item.path("id").asText();
                }
            }
            return null;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Chroma 解析集合 id 失败：" + exception.getMessage(), exception);
        }
    }

    private int countCollectionIfPresent(String name) {
        String collectionId = findCollectionId(name);
        if (collectionId == null) {
            return 0;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(chromaBaseUrl + String.format(COUNT_PATH, collectionId)))
                    .timeout(timeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Chroma 统计集合失败 HTTP " + response.statusCode()
                        + "：" + abbreviate(response.body()));
            }
            return objectMapper.readTree(response.body()).asInt();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Chroma 统计集合失败：" + exception.getMessage(), exception);
        }
    }

    /** 精选来源与上传原文分块按配额混合，确保既有外部可引用来源，也用到全文细节。 */
    static List<PaperEvidence> hybridMerge(
            List<PaperEvidence> curated, List<PaperEvidence> uploaded, int topK) {
        int limit = Math.max(1, topK);
        List<PaperEvidence> left = curated == null ? List.of() : curated;
        List<PaperEvidence> right = uploaded == null ? List.of() : uploaded;
        if (right.isEmpty()) {
            return List.copyOf(left.subList(0, Math.min(limit, left.size())));
        }

        int curatedQuota = Math.min(left.size(), Math.max(1, (limit + 1) / 2));
        int uploadedQuota = Math.min(right.size(), limit - curatedQuota);
        LinkedHashMap<String, PaperEvidence> merged = new LinkedHashMap<>();
        left.stream().limit(curatedQuota).forEach(item -> merged.putIfAbsent(item.sourceId(), item));
        right.stream().limit(uploadedQuota).forEach(item -> merged.putIfAbsent(item.sourceId(), item));
        left.forEach(item -> {
            if (merged.size() < limit) {
                merged.putIfAbsent(item.sourceId(), item);
            }
        });
        right.forEach(item -> {
            if (merged.size() < limit) {
                merged.putIfAbsent(item.sourceId(), item);
            }
        });
        return List.copyOf(merged.values());
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

    /** Chroma get 响应使用一维 ids/metadatas/documents，与 query 的二维数组不同。 */
    Optional<PaperEvidence> toSinglePaperEvidence(JsonNode collection) {
        JsonNode ids = collection.path("ids");
        if (!ids.isArray() || ids.isEmpty()) {
            return Optional.empty();
        }
        JsonNode metadata = collection.path("metadatas").path(0);
        String sourceId = metadata.path("source_id").asText("");
        String title = metadata.path("title").asText("");
        String content = collection.path("documents").path(0).asText("");
        String authors = metadata.path("authors").asText("");
        int year = metadata.path("year").isInt() ? metadata.path("year").asInt() : 0;
        if (title.isBlank() || content.isBlank() || sourceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new PaperEvidence(
                title,
                content,
                splitAuthors(authors),
                year == 0 ? null : year,
                doiOf(sourceId),
                pmidOf(sourceId),
                urlOf(sourceId)
        ));
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
