package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineModels.Hypothesis;
import com.aiscientist.ai.pipeline.PipelineModels.HypothesisResult;
import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;

/** ④ 假设生成 Agent：基于知识发现、文献与四库 RAG 生成可追溯候选假设。 */
@Service
public class HypothesisGenerationAgent {
    private static final String MODEL = "qwen-max";
    private static final int TOP_K = 5;
    /** 候选假设上限：超过则截断（保留下限 ≥2） */
    private static final int MAX_HYPOTHESES = 8;
    private static final String SYSTEM_PROMPT = """
            你是科研假设生成 Agent。根据 Research Gap、已知发现和检索证据，
            生成 3 至 5 个彼此不同、可证伪、可验证的科学假设。
            只返回 JSON：{"hypotheses":[{"summary":"","rationale":"",
            "technicalDetails":[""],"methods":[""],"reasoningChain":[""],
            "evidenceIds":["doi:...或pmid:...或url:..."]}]}。
            evidenceIds 只能从 allowedEvidenceIds 中选择；禁止虚构论文、来源和实验结果。
            每条假设必须包含非空的技术手段、方法、推理链和至少一个证据 ID。
            """;
    private final BailianClient bailianClient;
    private final RagSearchService ragSearchService;
    private final ObjectMapper objectMapper;
    /** 调试模式（RAG_MOCK_SAMPLES=true）：放宽证据白名单校验，便于无 RAG/临时关闭时跑通 */
    private final boolean mockSamples;

    public HypothesisGenerationAgent(BailianClient bailianClient,
                                     RagSearchService ragSearchService,
                                     ObjectMapper objectMapper,
                                     @Value("${vector.mock-samples:false}") boolean mockSamples) {
        this.bailianClient = bailianClient;
        this.ragSearchService = ragSearchService;
        this.objectMapper = objectMapper;
        this.mockSamples = mockSamples;
    }

    public HypothesisResult generate(String question, String domain,
                                     DiscoveryResult discovery,
                                     List<PaperEvidence> directPapers) {
        if (discovery == null) {
            throw new IllegalArgumentException("假设生成需要知识发现结果");
        }
        String query = buildQuery(question, discovery);
        List<PaperEvidence> papers = directPapers == null || directPapers.isEmpty()
                ? search("papers", query) : List.copyOf(directPapers);
        List<PaperEvidence> methods = search("methods", query);
        List<PaperEvidence> evidence = search("evidence", query);
        Set<String> allowed = new LinkedHashSet<>(discovery.references());
        papers.forEach(item -> allowed.add(item.sourceId()));
        methods.forEach(item -> allowed.add(item.sourceId()));
        evidence.forEach(item -> allowed.add(item.sourceId()));
        if (allowed.isEmpty()) {
            throw new IllegalStateException("假设生成未获得可追溯证据");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", question);
        payload.put("domain", domain == null || domain.isBlank() ? "通用科研" : domain);
        payload.put("selectedProblem", discovery.selectedProblem());
        payload.put("researchGaps", discovery.researchGaps());
        payload.put("knownFindings", discovery.knownFindings());
        payload.put("limitations", discovery.limitations());
        payload.put("conflicts", discovery.conflicts());
        payload.put("transferOpportunities", discovery.transferOpportunities());
        payload.put("papers", papers);
        payload.put("methodKnowledge", methods);
        payload.put("evidenceKnowledge", evidence);
        payload.put("allowedEvidenceIds", allowed);
        HypothesisResult result = callModel(payload);
        validate(result, allowed);
        // 上限 8：LLM 多生成候选假设时截断（保留下限 ≥2 已由 validate 保证），不中断
        List<Hypothesis> capped = result.hypotheses().size() <= MAX_HYPOTHESES
                ? result.hypotheses()
                : List.copyOf(result.hypotheses().subList(0, MAX_HYPOTHESES));
        return new HypothesisResult(capped);
    }

    private List<PaperEvidence> search(String collection, String query) {
        List<PaperEvidence> result = ragSearchService.search(collection, query, TOP_K);
        return result == null ? List.of() : List.copyOf(result);
    }

    private HypothesisResult callModel(Object payload) {
        try {
            String input = objectMapper.writeValueAsString(payload);
            String response = bailianClient.chat(MODEL, SYSTEM_PROMPT, input);
            return objectMapper.readValue(stripCodeFence(response), HypothesisResult.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("假设生成 Agent 返回了无效 JSON", exception);
        }
    }

    private void validate(HypothesisResult result, Set<String> allowed) {
        // 保留下限：至少 2 个候选假设（LLM 偶发多给不中断，上限 8 截断）
        if (result == null || result.hypotheses().size() < 2) {
            throw new IllegalStateException("假设生成结果必须包含至少 2 个候选假设");
        }
        for (Hypothesis item : result.hypotheses()) {
            if (item.technicalDetails().isEmpty() || item.methods().isEmpty()
                    || item.reasoningChain().isEmpty() || item.evidenceIds().isEmpty()) {
                throw new IllegalStateException("每个候选假设必须包含技术、方法、推理链和证据");
            }
            if (!mockSamples && !allowed.containsAll(item.evidenceIds())) {
                throw new IllegalStateException("候选假设引用了未提供的证据来源");
            }
        }
    }

    private String buildQuery(String question, DiscoveryResult discovery) {
        String gaps = discovery.researchGaps().stream()
                .map(gap -> gap.gap()).reduce("", (left, right) -> left + "；" + right);
        return String.join("；", question == null ? discovery.selectedProblem() : question,
                discovery.selectedProblem(), gaps);
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
