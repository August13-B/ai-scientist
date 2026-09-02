package com.challenge.aiscientist.vector;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vectors")
public class VectorController {
    private final ChromaVectorService vectorService;
    public VectorController(ChromaVectorService vectorService) { this.vectorService = vectorService; }
    @PostMapping("/documents") public DocumentResponse upsert(@Valid @RequestBody DocumentRequest request) {
        return new DocumentResponse(vectorService.upsert(request.document(), request.metadata() == null ? Map.of() : request.metadata()));
    }
    @PostMapping("/search") public JsonNode search(@Valid @RequestBody SearchRequest request) { return vectorService.query(request.query(), request.limit()); }
    public record DocumentRequest(@NotBlank String document, Map<String, Object> metadata) { }
    public record DocumentResponse(String id) { }
    public record SearchRequest(@NotBlank String query, int limit) { }
}
