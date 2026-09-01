package com.aiscientist.ai.wangwanying.evidence;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
@ConditionalOnProperty(name = "jiebang.agent.experiment-design.repository", havingValue = "milvus", matchIfMissing = true)
public class MilvusEvidenceRepository implements EvidenceRepository {
    private static final int LONG_QUERY_LENGTH = 24;
    private static final double FALLBACK_SCORE = 0.35;

    private final MilvusEmbeddingStore store;
    private final EmbeddingModel model;
    private final double minimumScore;

    public MilvusEvidenceRepository(
            EmbeddingModel model,
            @Value("${jiebang.agent.experiment-design.milvus.uri}") String uri,
            @Value("${jiebang.agent.experiment-design.milvus.token:}") String token,
            @Value("${jiebang.agent.experiment-design.milvus.collection}") String collection,
            @Value("${jiebang.agent.experiment-design.milvus.dimension}") int dimension,
            @Value("${jiebang.agent.experiment-design.milvus.minimum-score:0.45}") double minimumScore) {
        this.model = model;
        this.minimumScore = minimumScore;
        URI endpoint = URI.create(uri);
        var builder = MilvusEmbeddingStore.builder()
                .host(endpoint.getHost())
                .port(endpoint.getPort())
                .collectionName(collection)
                .dimension(dimension);
        if (token != null && !token.isBlank()) {
            builder.token(token.trim());
        }
        this.store = builder.build();
    }

    @Override
    public Evidence save(Evidence evidence) {
        TextSegment segment = toSegment(evidence);
        store.add(model.embed(segment).content(), segment);
        return evidence;
    }

    @Override
    public List<Evidence> findAll() {
        throw new UnsupportedOperationException("向量库不支持无条件全量扫描，请使用带q参数的检索");
    }

    @Override
    public List<Evidence> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Milvus检索必须提供q参数");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit必须在1到100之间");
        }

        Embedding queryEmbedding = model.embed(query.trim()).content();
        List<Evidence> matches = search(queryEmbedding, limit, minimumScore);
        if (matches.isEmpty() && query.codePointCount(0, query.length()) >= LONG_QUERY_LENGTH) {
            matches = search(queryEmbedding, limit, Math.min(minimumScore, FALLBACK_SCORE));
        }
        return matches;
    }

    private List<Evidence> search(Embedding queryEmbedding, int limit, double score) {
        var request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(limit)
                .minScore(score)
                .build();
        return store.search(request).matches().stream()
                .map(EmbeddingMatch::embedded)
                .map(this::fromSegment)
                .toList();
    }

    private TextSegment toSegment(Evidence evidence) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("subject", evidence.subject());
        metadata.put("predicate", evidence.predicate());
        metadata.put("object", evidence.object());
        metadata.put("statement", evidence.statement());
        metadata.put("doi", evidence.doi());
        metadata.put("pmid", evidence.pmid());
        metadata.put("sourceTitle", evidence.sourceTitle());
        metadata.put("year", String.valueOf(evidence.year()));
        metadata.put("tags", String.join("|", evidence.tags()));
        metadata.put("taskId", evidence.taskId());
        metadata.put("runId", evidence.runId());
        metadata.put("evidenceId", evidence.evidenceId());
        metadata.put("modality", evidence.modality().name());
        metadata.put("fileName", evidence.fileName());
        metadata.put("page", evidence.page() == null ? "" : evidence.page().toString());
        metadata.put("sourceUri", evidence.sourceUri());
        metadata.put("confidence", String.valueOf(evidence.confidence()));
        metadata.put("observation", evidence.observation());
        return TextSegment.from(evidence.searchableText(), dev.langchain4j.data.document.Metadata.from(metadata));
    }

    private Evidence fromSegment(TextSegment segment) {
        var metadata = segment.metadata();
        String tags = metadata.getString("tags");
        String page = metadata.getString("page");
        String modality = metadata.getString("modality");
        String taskId = metadata.getString("taskId");
        String runId = metadata.getString("runId");
        String evidenceId = metadata.getString("evidenceId");
        String confidence = metadata.getString("confidence");
        return new Evidence(
                taskId == null || taskId.isBlank() ? "legacy-task" : taskId,
                runId == null || runId.isBlank() ? "legacy-run" : runId,
                evidenceId == null || evidenceId.isBlank() ? "legacy-" + Integer.toHexString(segment.text().hashCode()) : evidenceId,
                modality == null || modality.isBlank() ? EvidenceModality.TEXT : EvidenceModality.valueOf(modality),
                metadata.getString("subject"), metadata.getString("predicate"), metadata.getString("object"),
                metadata.getString("statement"), metadata.getString("doi"), metadata.getString("pmid"),
                metadata.getString("sourceTitle"), Integer.parseInt(metadata.getString("year")),
                metadata.getString("fileName"), page == null || page.isBlank() ? null : Integer.parseInt(page),
                metadata.getString("sourceUri"), confidence == null || confidence.isBlank() ? 1.0 : Double.parseDouble(confidence),
                metadata.getString("observation"),
                tags == null || tags.isBlank() ? List.of() : List.of(tags.split("\\|")));
    }}
