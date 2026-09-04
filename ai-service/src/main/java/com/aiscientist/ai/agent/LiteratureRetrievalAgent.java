package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineModels.CitationChain;
import com.aiscientist.ai.pipeline.PipelineModels.KeyFinding;
import com.aiscientist.ai.pipeline.PipelineModels.LiteratureResult;
import com.aiscientist.ai.pipeline.PipelineModels.QuestionQuery;
import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;

/**
 * ② 文献检索 Agent（张睿负责）。
 *
 * <p>流程：RAG 检索增强 → LLM 提炼（仿 ③ KnowledgeDiscoveryAgent 模式）：</p>
 * <ol>
 *   <li><b>检索</b>：对 ① 输出的每条 {@code subQueries} 子查询，在论文库 + 证据库
 *       各检索 topK=5（{@link RagSearchService}，返回已对齐 {@link PaperEvidence} 契约），
 *       按 {@code sourceId} 去重聚合；少于 2 篇不同来源直接判失败；超过 {@code MAX_PAPERS=15}
 *       按首现序裁剪（子查询优先级保留）。</li>
 *   <li><b>提炼（动态路由）</b>：召回 ≤8 篇走单次批量（1 次 LLM 输出 keyFindings +
 *       citationChains）；&gt;8 篇走两阶段（分组逐篇提炼 keyFindings → 跨篇生成
 *       citationChains），控制单次 token 长度。</li>
 *   <li><b>确定性补全与白名单校验</b>：模型漏掉某篇时直接依据该篇原文补一条发现；
 *       每条 KeyFinding / CitationChain 的 evidenceIds 仍必须 ∈ 召回 {@code sourceId}，
 *       防止 LLM 漏篇导致管线中断，同时继续拦截虚构来源。</li>
 * </ol>
 *
 * <p>RAG 接口预留：检索唯一入口为 {@link RagSearchService#search(String, String, int)}，
 * 知识库范围 {@code KNOWLEDGE_BASES = {papers, evidence}}（② 分工：论文库 + 证据库）；
 * 需要按域扩展方法库/数据集库时改此处即可，检索/提炼/校验链路不变。</p>
 *
 * <p>输出契约：{@link LiteratureResult}（papers + keyFindings + citationChains），
 * ④ 假设生成消费 papers；⑤ 评估可据 papers 做本地引用反向比对。</p>
 */
@Service
public class LiteratureRetrievalAgent {

    /** 文献检索为重任务（召回质量决定 ④⑤ 下限）：走 Qwen-Max 分级 */
    private static final String MODEL = "qwen-max";

    /** ② 分工知识库：论文库 + 证据库 */
    private static final List<String> KNOWLEDGE_BASES = List.of("papers", "evidence");

    private static final int TOP_K_PER_QUERY = 5;
    private static final int MAX_PAPERS = 15;
    /** ≤8 篇单次批量提炼，>8 篇两阶段 */
    private static final int SINGLE_PASS_LIMIT = 8;
    /** 两阶段分组大小（控制单次 LLM 输入 token） */
    private static final int GROUP_SIZE = 5;
    /** 送 LLM 的单篇正文截断长度（避免超长输入） */
    private static final int CONTENT_LIMIT = 600;

    private static final String SINGLE_PASS_PROMPT = """
            你是文献检索提炼 Agent。基于召回文献提炼「关键发现」与「文献间逻辑关联」。
            规则：
            1. keyFindings：每条发现必须覆盖至少一篇给定文献（全部给定文献都要被覆盖）；
            2. citationChains：概括文献间逻辑关联（如 A 方法基于 B 理论，共同支撑某论点），可为空数组；
            3. evidenceIds 只能从给定文献的 sourceId 中选择；禁止虚构文献、来源与发现；
            4. 只返回 JSON（不要 markdown 代码块）：
            {"keyFindings":[{"finding":"...","evidenceIds":["doi:..."]}],
             "citationChains":[{"chain":"...","evidenceIds":["doi:..."]}]}
            """;

    private static final String GROUP_EXTRACT_PROMPT = """
            你是文献检索提炼 Agent。对给定的一组召回文献逐篇提炼关键发现。
            规则：
            1. 每一篇给定文献都必须被至少一条 finding 覆盖（evidenceIds 指向它）；
            2. evidenceIds 只能从给定文献的 sourceId 中选择；禁止虚构文献、来源与发现；
            3. 只返回 JSON：{"keyFindings":[{"finding":"...","evidenceIds":["doi:..."]}]}
            """;

    private static final String CROSS_LINK_PROMPT = """
            你是文献检索提炼 Agent。基于全部召回文献及其关键发现，概括文献间逻辑关联。
            规则：
            1. citationChains 描述跨文献的逻辑关联（如方法传承、理论支撑、结论互补/冲突）；
            2. evidenceIds 只能从给定文献的 sourceId 中选择；禁止虚构文献、来源与关联；
            3. 只返回 JSON：{"citationChains":[{"chain":"...","evidenceIds":["doi:..."]}]}
            """;

    private final BailianClient bailianClient;
    private final RagSearchService ragSearchService;
    private final ObjectMapper objectMapper;
    /** 调试模式（RAG_MOCK_SAMPLES=true）：放宽覆盖性校验（LLM 偶发漏篇不打回），生产模式保持严格 */
    private final boolean mockSamples;

    public LiteratureRetrievalAgent(
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

    /**
     * 检索并提炼文献。
     *
     * @param query ① 问题理解的输出（subQueries 为空时退化为按原始问题检索）
     * @return {@link LiteratureResult}（papers/keyFindings/citationChains）
     * @throws IllegalStateException 召回不足 / 模型输出无效 / 白名单校验失败
     */
    public LiteratureResult retrieve(QuestionQuery query) {
        if (query == null || isBlank(query.originalQuestion())) {
            throw new IllegalArgumentException("文献检索需要问题理解输出（originalQuestion 非空）");
        }

        List<PaperEvidence> papers = retrievePapers(query);
        List<KeyFinding> keyFindings;
        List<CitationChain> citationChains;

        if (papers.size() <= SINGLE_PASS_LIMIT) {
            Extraction extraction = call(SINGLE_PASS_PROMPT, payload(query, papers), Extraction.class);
            keyFindings = extraction.keyFindings();
            citationChains = extraction.citationChains();
        } else {
            keyFindings = extractInGroups(query, papers);
            citationChains = linkAcross(query, papers, keyFindings);
        }

        if (!mockSamples) {
            keyFindings = sanitizeFindings(keyFindings, papers);
            citationChains = sanitizeChains(citationChains, papers);
            keyFindings = ensureCoverage(papers, keyFindings);
        }
        validate(keyFindings, citationChains, papers);
        // 规范化重建：触发 compact 构造器（文本非空 + 不可变列表）；keyFindings 可为空（调试模式放宽）
        List<KeyFinding> normalizedFindings = keyFindings == null
                ? List.of()
                : keyFindings.stream()
                        .map(item -> new KeyFinding(item.finding().trim(),
                                List.copyOf(item.evidenceIds())))
                        .toList();
        List<CitationChain> normalizedChains = citationChains == null
                ? List.of()
                : citationChains.stream()
                        .map(item -> new CitationChain(item.chain().trim(),
                                List.copyOf(item.evidenceIds())))
                        .toList();
        return new LiteratureResult(
                List.copyOf(papers),
                normalizedFindings,
                normalizedChains
        );
    }

    // ==================== 检索 ====================

    /** 逐子查询 × 各知识库检索 → sourceId 去重 → <2 篇失败 → 裁剪 MAX_PAPERS */
    private List<PaperEvidence> retrievePapers(QuestionQuery query) {
        List<String> subQueries = query.subQueries().isEmpty()
                ? List.of(query.originalQuestion())
                : query.subQueries();

        Map<String, PaperEvidence> distinct = new LinkedHashMap<>();
        for (String subQuery : subQueries) {
            for (String knowledgeBase : KNOWLEDGE_BASES) {
                List<PaperEvidence> hits = ragSearchService.search(
                        knowledgeBase, subQuery, TOP_K_PER_QUERY);
                if (hits != null) {
                    hits.forEach(paper -> distinct.putIfAbsent(paper.sourceId(), paper));
                }
            }
        }

        List<PaperEvidence> all = List.copyOf(distinct.values());
        if (all.size() < 2) {
            throw new IllegalStateException("文献检索至少需要两篇不同来源论文（当前 "
                    + all.size() + " 篇）");
        }
        return all.size() <= MAX_PAPERS ? all : all.subList(0, MAX_PAPERS);
    }

    // ==================== 提炼（动态路由） ====================

    /** >8 篇：分组逐篇提炼 keyFindings（每组 ≤5 篇，要求组内每篇覆盖） */
    private List<KeyFinding> extractInGroups(QuestionQuery query, List<PaperEvidence> papers) {
        List<KeyFinding> findings = new ArrayList<>();
        for (int start = 0; start < papers.size(); start += GROUP_SIZE) {
            List<PaperEvidence> group = papers.subList(
                    start, Math.min(start + GROUP_SIZE, papers.size()));
            Map<String, Object> groupPayload = new LinkedHashMap<>();
            groupPayload.put("domain", query.domain());
            groupPayload.put("groupPapers", payloadPapers(group));
            GroupExtraction extraction = call(
                    GROUP_EXTRACT_PROMPT, groupPayload, GroupExtraction.class);
            List<KeyFinding> groupFindings = extraction.keyFindings();
            if (groupFindings == null || groupFindings.isEmpty()) {
                throw new IllegalStateException("文献检索提炼：分组未返回关键发现");
            }
            findings.addAll(groupFindings);
        }
        return findings;
    }

    /** >8 篇：跨篇生成文献间逻辑关联 */
    private List<CitationChain> linkAcross(
            QuestionQuery query,
            List<PaperEvidence> papers,
            List<KeyFinding> findings
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("domain", query.domain());
        payload.put("subQueries", query.subQueries());
        payload.put("papers", payloadPapers(papers));
        payload.put("keyFindings", findings);
        CrossExtraction extraction = call(CROSS_LINK_PROMPT, payload, CrossExtraction.class);
        return extraction.citationChains();
    }

    // ==================== 校验与规范化 ====================

    /**
     * 过滤模型偶发生成的非白名单 evidenceId；没有任何真实证据的发现直接丢弃，
     * 随后由 ensureCoverage 使用召回论文原文保守补全，绝不保留虚构引用。
     */
    private List<KeyFinding> sanitizeFindings(
            List<KeyFinding> findings, List<PaperEvidence> papers) {
        if (findings == null) {
            return List.of();
        }
        Set<String> allowed = allowedSources(papers);
        return findings.stream()
                .filter(item -> item != null && !isBlank(item.finding()))
                .map(item -> new KeyFinding(item.finding(), validSources(item.evidenceIds(), allowed)))
                .filter(item -> !item.evidenceIds().isEmpty())
                .toList();
    }

    /** 非白名单逻辑链不参与后续推理；混合引用仅保留真实召回来源。 */
    private List<CitationChain> sanitizeChains(
            List<CitationChain> chains, List<PaperEvidence> papers) {
        if (chains == null) {
            return List.of();
        }
        Set<String> allowed = allowedSources(papers);
        return chains.stream()
                .filter(item -> item != null && !isBlank(item.chain()))
                .map(item -> new CitationChain(item.chain(), validSources(item.evidenceIds(), allowed)))
                .filter(item -> !item.evidenceIds().isEmpty())
                .toList();
    }

    private Set<String> allowedSources(List<PaperEvidence> papers) {
        return papers.stream()
                .map(PaperEvidence::sourceId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private List<String> validSources(List<String> evidenceIds, Set<String> allowed) {
        if (evidenceIds == null) {
            return List.of();
        }
        return evidenceIds.stream()
                .filter(allowed::contains)
                .distinct()
                .toList();
    }

    /**
     * 白名单强校验：
     * 1. keyFindings 非空（调试模式放宽，临时关闭 RAG 时允许为空）；2. 每篇召回文献必须被至少一条 finding 覆盖（防漏篇）；
     * 3. 每条 finding/chain 的 evidenceIds 非空且全部 ∈ 召回 sourceId（防虚构）。
     */
    private void validate(List<KeyFinding> keyFindings, List<CitationChain> chains,
                          List<PaperEvidence> papers) {
        if (!mockSamples && (keyFindings == null || keyFindings.isEmpty())) {
            throw new IllegalStateException("文献检索结果必须包含至少一条关键发现");
        }
        Set<String> allowed = allowedSources(papers);
        // 调试模式放宽覆盖校验（LLM 偶发漏篇不打回）；白名单校验始终保留（防虚构）
        if (!mockSamples) {
            requireCoverage(papers, keyFindings);
        }
        for (KeyFinding finding : keyFindings) {
            if (isBlank(finding.finding())) {
                throw new IllegalStateException("文献检索结果包含空白关键发现");
            }
            requireSources(finding.evidenceIds(), allowed, "关键发现");
        }
        if (chains != null) {
            for (CitationChain chain : chains) {
                if (isBlank(chain.chain())) {
                    throw new IllegalStateException("文献检索结果包含空白逻辑关联");
                }
                requireSources(chain.evidenceIds(), allowed, "逻辑关联");
            }
        }
    }

    /** 组内/全量覆盖：每篇 sourceId 必须出现在至少一条 finding 的证据中 */
    private void requireCoverage(List<PaperEvidence> papers, List<KeyFinding> findings) {
        Set<String> covered = findings.stream()
                .flatMap(finding -> finding.evidenceIds() == null
                        ? java.util.stream.Stream.empty()
                        : finding.evidenceIds().stream())
                .collect(Collectors.toSet());
        List<String> missed = papers.stream()
                .map(PaperEvidence::sourceId)
                .filter(sourceId -> !covered.contains(sourceId))
                .toList();
        if (!missed.isEmpty()) {
            throw new IllegalStateException("文献检索提炼未覆盖全部召回文献：" + missed);
        }
    }

    /**
     * 模型偶发漏篇时，以召回条目自身的标题和原文片段生成保守发现。
     * 该补全不引入新知识，evidenceId 使用原始 sourceId，因而仍可精确溯源。
     */
    private List<KeyFinding> ensureCoverage(
            List<PaperEvidence> papers, List<KeyFinding> findings) {
        List<KeyFinding> completed = new ArrayList<>(findings == null ? List.of() : findings);
        Set<String> covered = completed.stream()
                .flatMap(finding -> finding.evidenceIds() == null
                        ? java.util.stream.Stream.empty()
                        : finding.evidenceIds().stream())
                .collect(Collectors.toSet());
        for (PaperEvidence paper : papers) {
            if (covered.contains(paper.sourceId())) {
                continue;
            }
            String content = paper.content().replaceAll("\\s+", " ").trim();
            if (content.length() > 220) {
                content = content.substring(0, 220) + "……";
            }
            completed.add(new KeyFinding(
                    "文献《" + paper.title() + "》提供的可追溯原文证据：" + content,
                    List.of(paper.sourceId())));
            covered.add(paper.sourceId());
        }
        return List.copyOf(completed);
    }

    private void requireSources(List<String> evidenceIds, Set<String> allowed, String what) {
        // 调试模式（临时关 RAG）：仅要求非空，不查 ∈ 召回来源（mock 样例下 LLM 引用可能超出）
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new IllegalStateException("文献检索" + what + "引用了未提供的文献来源");
        }
        if (!mockSamples && !allowed.containsAll(evidenceIds)) {
            throw new IllegalStateException("文献检索" + what + "引用了未提供的文献来源");
        }
    }

    // ==================== LLM 调用 ====================

    private <T> T call(String systemPrompt, Object input, Class<T> outputType) {
        try {
            String userMessage = objectMapper.writeValueAsString(input);
            String response = bailianClient.chat(MODEL, systemPrompt, userMessage);
            return objectMapper.readValue(stripCodeFence(response), outputType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("文献检索提炼返回了无效 JSON", exception);
        }
    }

    /** 单次批量输出 DTO */
    private record Extraction(List<KeyFinding> keyFindings, List<CitationChain> citationChains) {
    }

    /** 分组逐篇输出 DTO */
    private record GroupExtraction(List<KeyFinding> keyFindings) {
    }

    /** 跨篇关联输出 DTO */
    private record CrossExtraction(List<CitationChain> citationChains) {
    }

    private Map<String, Object> payload(QuestionQuery query, List<PaperEvidence> papers) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("domain", query.domain());
        payload.put("subQueries", query.subQueries());
        payload.put("papers", payloadPapers(papers));
        return payload;
    }

    /** 正文截断后再送 LLM（控制 token） */
    private List<Map<String, Object>> payloadPapers(List<PaperEvidence> papers) {
        return papers.stream().map(paper -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceId", paper.sourceId());
            item.put("title", paper.title());
            item.put("content", abbreviate(paper.content()));
            item.put("authors", paper.authors());
            item.put("year", paper.year());
            return item;
        }).toList();
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

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= CONTENT_LIMIT ? text : text.substring(0, CONTENT_LIMIT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
