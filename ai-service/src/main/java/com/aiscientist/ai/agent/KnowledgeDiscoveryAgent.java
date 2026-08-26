package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.CrossPaperAnalysis;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryRequest;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.EvidenceExtraction;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;

/** 跨论文知识发现服务：证据提取 → 跨论文比较 → Research Gap 排序。 */
@Service
public class KnowledgeDiscoveryAgent {

    private static final String MODEL = "qwen-plus";

    private final BailianClient bailianClient;
    private final RagSearchService ragSearchService;
    private final ObjectMapper objectMapper;

    public KnowledgeDiscoveryAgent(
            BailianClient bailianClient,
            RagSearchService ragSearchService,
            ObjectMapper objectMapper
    ) {
        this.bailianClient = bailianClient;
        this.ragSearchService = ragSearchService;
        this.objectMapper = objectMapper;
    }

    public DiscoveryResult discover(DiscoveryRequest request) {
        List<PaperEvidence> evidence = loadEvidence(request);
        Set<String> allowedSources = evidence.stream()
                .map(PaperEvidence::sourceId)
                .collect(Collectors.toUnmodifiableSet());

        EvidenceExtraction extraction = call(
                "证据提取",
                KnowledgeDiscoveryPrompts.extraction(),
                payload(request, "papers", evidence),
                EvidenceExtraction.class
        );
        requireSources(extraction.papers().stream()
                .map(KnowledgeDiscoveryModels.PaperAnalysis::sourceId)
                .toList(), allowedSources);

        CrossPaperAnalysis comparison = call(
                "跨论文比较",
                KnowledgeDiscoveryPrompts.comparison(),
                payload(request, "paperAnalyses", extraction.papers()),
                CrossPaperAnalysis.class
        );

        Map<String, Object> rankingInput = payload(request, "comparison", comparison);
        rankingInput.put("paperAnalyses", extraction.papers());
        DiscoveryResult result = call(
                "Research Gap 排序",
                KnowledgeDiscoveryPrompts.ranking(),
                rankingInput,
                DiscoveryResult.class
        );
        validateResultSources(result, allowedSources);
        return result;
    }

    private List<PaperEvidence> loadEvidence(DiscoveryRequest request) {
        if (!request.evidence().isEmpty()) {
            return request.evidence();
        }
        List<Object> results = ragSearchService.search(
                "papers", request.question(), request.topK());
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("论文检索未返回可追溯证据");
        }
        try {
            return results.stream()
                    .map(this::toPaperEvidence)
                    .toList();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("论文检索返回了无效证据", exception);
        }
    }

    private PaperEvidence toPaperEvidence(Object result) {
        if (result instanceof PaperEvidence paperEvidence) {
            return paperEvidence;
        }
        return objectMapper.convertValue(result, PaperEvidence.class);
    }

    private Map<String, Object> payload(
            DiscoveryRequest request,
            String dataName,
            Object data
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", request.question());
        payload.put("domain", request.domain());
        payload.put(dataName, data);
        return payload;
    }

    private <T> T call(
            String stage,
            String systemPrompt,
            Object input,
            Class<T> outputType
    ) {
        try {
            String userMessage = objectMapper.writeValueAsString(input);
            String response = bailianClient.chat(MODEL, systemPrompt, userMessage);
            return objectMapper.readValue(stripCodeFence(response), outputType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException(stage + "返回了无效 JSON", exception);
        }
    }

    private void validateResultSources(
            DiscoveryResult result,
            Set<String> allowedSources
    ) {
        requireSources(result.references(), allowedSources);
        result.researchGaps().forEach(gap ->
                requireSources(gap.evidenceIds(), allowedSources));
    }

    private void requireSources(List<String> sources, Set<String> allowedSources) {
        if (sources.isEmpty() || !allowedSources.containsAll(sources)) {
            throw new IllegalStateException("知识发现结果包含未提供的文献来源");
        }
    }

    private String stripCodeFence(String response) {
        if (response == null) {
            throw new IllegalArgumentException("model response must not be null");
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            int firstLineEnd = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
                throw new IllegalArgumentException("invalid JSON code fence");
            }
            json = json.substring(firstLineEnd + 1, lastFence).trim();
        }
        return json;
    }
}
