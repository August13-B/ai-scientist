package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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

    /** 知识发现为重任务：走 Qwen-Max 分级（BailianClient 内部映射到 QWEN_MODEL） */
    private static final String MODEL = "qwen-max";

    private final BailianClient bailianClient;
    private final RagSearchService ragSearchService;
    private final ObjectMapper objectMapper;
    /** 调试模式（RAG_MOCK_SAMPLES=true）：放宽来源白名单与覆盖性校验，便于无 RAG/临时关闭时跑通 */
    private final boolean mockSamples;

    public KnowledgeDiscoveryAgent(
            BailianClient bailianClient,
            RagSearchService ragSearchService,
            ObjectMapper objectMapper,
            @Value("${vector.mock-samples:false}") boolean mockSamples
    ) {
        this.bailianClient = bailianClient;
        this.ragSearchService = ragSearchService;
        this.objectMapper = objectMapper;
        this.mockSamples = mockSamples;
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
        requireCompleteExtraction(extraction, allowedSources);

        CrossPaperAnalysis comparison = call(
                "跨论文比较",
                KnowledgeDiscoveryPrompts.comparison(),
                payload(request, "paperAnalyses", extraction.papers()),
                CrossPaperAnalysis.class
        );

        Map<String, Object> rankingInput = payload(request, "comparison", comparison);
        rankingInput.put("paperAnalyses", extraction.papers());
        DiscoveryResult ranked = call(
                "Research Gap 排序",
                KnowledgeDiscoveryPrompts.ranking(),
                rankingInput,
                DiscoveryResult.class
        );
        DiscoveryResult result = new DiscoveryResult(
                ranked.knownFindings(),
                ranked.limitations(),
                ranked.conflicts(),
                comparison.transferOpportunities(),
                ranked.researchGaps(),
                ranked.selectedProblem(),
                ranked.paperTitle(),
                ranked.paperAbstract(),
                ranked.references()
        );
        validateResultSources(result, allowedSources);
        return result;
    }

    private List<PaperEvidence> loadEvidence(DiscoveryRequest request) {
        List<PaperEvidence> evidence;
        if (!request.evidence().isEmpty()) {
            evidence = request.evidence();
        } else {
            // RAG 检索返回已是 PaperEvidence 契约（RagSearchService 保证字段对齐）
            List<PaperEvidence> results = ragSearchService.search(
                    "papers", request.question(), request.topK());
            if (results == null || results.isEmpty()) {
                throw new IllegalArgumentException("论文检索未返回可追溯证据");
            }
            evidence = results;
        }

        Map<String, PaperEvidence> distinct = new LinkedHashMap<>();
        evidence.forEach(paper -> distinct.putIfAbsent(paper.sourceId(), paper));
        if (distinct.size() < 2) {
            throw new IllegalArgumentException("知识发现至少需要两篇不同来源论文");
        }
        return List.copyOf(distinct.values());
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
        if (result.researchGaps().isEmpty()) {
            throw new IllegalStateException(
                    "知识发现结果至少包含一个 Research Gap");
        }
        // 调试模式（临时关 RAG）：放宽来源白名单与 gap 覆盖校验，仅保留非空/基本结构
        if (mockSamples) {
            return;
        }
        requireSources(result.references(), allowedSources);
        result.researchGaps().forEach(gap ->
                requireSources(gap.evidenceIds(), allowedSources));
        Set<String> gapSources = result.researchGaps().stream()
                .flatMap(gap -> gap.evidenceIds().stream())
                .collect(Collectors.toUnmodifiableSet());
        if (!result.references().containsAll(gapSources)) {
            throw new IllegalStateException(
                    "最终 references 未覆盖 Research Gap 证据");
        }
    }

    private void requireCompleteExtraction(
            EvidenceExtraction extraction,
            Set<String> allowedSources
    ) {
        // 调试模式（临时关 RAG）：不强制逐篇覆盖输入来源（mock 样例可能不稳定）
        if (mockSamples) {
            return;
        }
        List<String> sources = extraction.papers().stream()
                .map(KnowledgeDiscoveryModels.PaperAnalysis::sourceId)
                .toList();
        Set<String> uniqueSources = Set.copyOf(sources);
        if (sources.size() != allowedSources.size()
                || uniqueSources.size() != sources.size()
                || !uniqueSources.equals(allowedSources)) {
            throw new IllegalStateException(
                    "证据提取结果必须覆盖每个输入来源且不得重复");
        }
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
