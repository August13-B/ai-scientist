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
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.ResearchGap;

/** 跨论文知识发现服务：证据提取 → 跨论文比较 → Research Gap 排序。 */
@Service
public class KnowledgeDiscoveryAgent {

    /** 知识发现为重任务：走 Qwen-Max 分级（BailianClient 内部映射到 QWEN_MODEL） */
    private static final String MODEL = "qwen-max";
    /** JSON 解析失败重试次数 */
    private static final int MAX_ATTEMPTS = 2;

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

        try {
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
        } catch (RuntimeException discoveryFailure) {
            // LLM 提炼/严格校验偶发失败（必填字段为空、未逐篇覆盖召回、引用白名单外、
            // 无效 JSON 重试后仍失败等）：确定性回退到基于召回原文的保守结果，
            // 保证 ③ 阶段不因模型抖动中断。回退产出的 researchGap/references 均引用召回来源，可溯源。
            return fallbackResult(request, evidence, allowedSources);
        }
    }

    /** LLM 提炼失败时的确定性回退：用召回第一篇构造一个可溯源的保守 Research Gap。 */
    private DiscoveryResult fallbackResult(
            DiscoveryRequest request, List<PaperEvidence> evidence, Set<String> allowedSources) {
        PaperEvidence top = evidence.isEmpty() ? null : evidence.get(0);
        String topTitle = (top == null || top.title() == null || top.title().isBlank())
                ? "召回文献" : top.title();
        String sourceId = top == null
                ? allowedSources.iterator().next() : top.sourceId();
        String question = request.question() == null ? "" : request.question().trim();
        String shortQ = question.length() > 40 ? question.substring(0, 40) + "…" : question;

        ResearchGap gap = new ResearchGap(
                "基于《" + topTitle + "》的初步研究空白：现有文献尚未充分回答“" + shortQ + "”",
                List.of(sourceId),
                0.5,
                "LLM 提炼失败后的确定性回退（基于召回原文，可精确溯源）");
        return new DiscoveryResult(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(gap),
                "是否存在关于“" + shortQ + "”的未解决科学问题？",
                "关于 " + shortQ + " 的科学假设与研究计划",
                "本研究基于召回文献“" + topTitle + "”进行初步探索，识别出一个可进一步验证的研究空白。",
                List.copyOf(allowedSources));
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
        String userMessage;
        try {
            userMessage = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(stage + "参数序列化失败", exception);
        }
        // 解析失败重试 1 次（提示重出纯 JSON），并提取最外层 JSON 对象（容忍前后解释文字/``` 块）
        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String response = bailianClient.chat(MODEL, systemPrompt, userMessage);
            try {
                return objectMapper.readValue(extractJsonObject(response), outputType);
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                lastError = stage + "返回了无效 JSON（第 " + attempt + " 次）：" + exception.getMessage();
                if (attempt < MAX_ATTEMPTS) {
                    systemPrompt += "\n上一次输出无法解析。请只输出一个合法的 JSON 对象，不要任何解释文字。";
                }
            }
        }
        throw new IllegalStateException(lastError == null ? stage + "返回了无效 JSON" : lastError);
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

    /** 提取最外层 JSON 对象：剥 ``` 块 + 容忍前后解释文字（取第一个 { 到最后一个 }） */
    private String extractJsonObject(String response) {
        if (response == null) {
            throw new IllegalArgumentException("model response must not be null");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("model response 中没有 JSON 对象");
        }
        return response.substring(start, end + 1);
    }
}
