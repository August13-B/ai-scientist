package com.challenge.aiscientist.vector;

import com.challenge.aiscientist.config.ChromaProperties;
import com.challenge.aiscientist.llm.DashScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class ChromaVectorService {
    private final ChromaProperties properties;
    private final DashScopeService dashScopeService;
    private final Map<String, StoredDocument> localDocuments = new ConcurrentHashMap<>();
    private volatile String collectionId;

    public ChromaVectorService(ChromaProperties properties, DashScopeService dashScopeService) {
        this.properties = properties;
        this.dashScopeService = dashScopeService;
    }

    public String upsert(String document, Map<String, Object> metadata) {
        String id = UUID.randomUUID().toString();
        List<Double> embedding = dashScopeService.embed(document);
        if (!properties.enabled()) {
            localDocuments.put(id, new StoredDocument(document, metadata, embedding));
            return id;
        }
        client().post().uri(collectionPath() + "/upsert").contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("ids", List.of(id), "documents", List.of(document), "embeddings", List.of(embedding), "metadatas", List.of(metadata)))
            .retrieve().toBodilessEntity();
        return id;
    }

    public JsonNode query(String query, int limit) {
        List<Double> embedding = dashScopeService.embed(query);
        if (!properties.enabled()) return localQuery(embedding, limit);
        return client().post().uri(collectionPath() + "/query").contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("query_embeddings", List.of(embedding), "n_results", Math.min(Math.max(limit, 1), 20),
                "include", List.of("documents", "metadatas", "distances")))
            .retrieve().body(JsonNode.class);
    }

    private String collectionPath() { return databasePath() + "/collections/" + resolveCollectionId(); }
    private String databasePath() { return "/api/v2/tenants/" + properties.tenant() + "/databases/" + properties.database(); }

    private String resolveCollectionId() {
        if (collectionId != null) return collectionId;
        synchronized (this) {
            if (collectionId != null) return collectionId;
            try {
                JsonNode collections = client().get().uri(databasePath() + "/collections").retrieve().body(JsonNode.class);
                for (JsonNode collection : collections) {
                    if (properties.collection().equals(collection.path("name").asText())) {
                        return collectionId = collection.path("id").asText();
                    }
                }
                JsonNode created = client().post().uri(databasePath() + "/collections").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", properties.collection(), "get_or_create", true)).retrieve().body(JsonNode.class);
                return collectionId = created.path("id").asText();
            } catch (Exception exception) {
                throw new ResponseStatusException(BAD_GATEWAY, "Chroma is unavailable. Run docker compose up -d first.");
            }
        }
    }

    private RestClient client() { return RestClient.builder().baseUrl(properties.baseUrl()).build(); }

    private JsonNode localQuery(List<Double> queryVector, int limit) {
        List<Map<String, Object>> results = localDocuments.entrySet().stream()
            .map(entry -> Map.<String, Object>of("id", entry.getKey(), "document", entry.getValue().document(),
                "metadata", entry.getValue().metadata(), "distance", 1 - cosine(queryVector, entry.getValue().embedding())))
            .sorted((left, right) -> Double.compare((Double) left.get("distance"), (Double) right.get("distance")))
            .limit(Math.min(Math.max(limit, 1), 20)).toList();
        return new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("mode", "memory", "results", results));
    }

    private double cosine(List<Double> left, List<Double> right) {
        double dot = 0, leftNorm = 0, rightNorm = 0;
        for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
            dot += left.get(index) * right.get(index);
            leftNorm += left.get(index) * left.get(index);
            rightNorm += right.get(index) * right.get(index);
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / Math.sqrt(leftNorm * rightNorm);
    }

    private record StoredDocument(String document, Map<String, Object> metadata, List<Double> embedding) { }
}
