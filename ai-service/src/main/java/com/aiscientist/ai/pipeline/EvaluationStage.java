package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.verify.CitationCheck;
import com.aiscientist.ai.verify.CitationStatus;
import com.aiscientist.ai.verify.CitationVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ⑤ 科学假设评估阶段接入适配器（钱思妤负责，核心质量关卡）。
 *
 * <p>实现 {@link PipelineAgent}：从 {@code ctx.getHypothesis()} 读候选假设，
 * 依次执行「幻觉检测（引用真实性逐条核验）→ 四维评分 → 打回判定」，
 * 把结果写入 {@code ctx.setEvaluation()}。</p>
 *
 * <p>评分分工（关键设计）：</p>
 * <ul>
 *   <li>创新性、可行性：由 Qwen 大模型生成（复用团队 {@link BailianClient}）；</li>
 *   <li>引用真实性：确定性核验（{@link CitationVerifier} 逐条反查真实文献），不交给 LLM，保证「引用严禁虚构」可复现；</li>
 *   <li>数据可获得性：确定性启发式。</li>
 * </ul>
 *
 * <p>打回红线：无引用、无一条真实引用核验通过、检出疑似虚构、存在无法核验项，
 * 均不得放行（抛异常中断评估阶段，交管线包装为阶段失败）。</p>
 */
@Component
public class EvaluationStage implements PipelineAgent {

    private static final double THRESHOLD = 0.6;
    private static final double W_INNOVATION = 0.25;
    private static final double W_FEASIBILITY = 0.25;
    private static final double W_CITATION = 0.35;
    private static final double W_DATA = 0.15;

    private static final Pattern SCORE_PATTERN =
            Pattern.compile("\"score\"\\s*:\\s*([0-9]{1,3}(?:\\.[0-9]+)?)");

    private static final String SCORING_PROMPT = """
            你是严谨的科学假设评审专家。请对下面这条科学假设的「%s」维度打分（0-100 整数）。
            评分维度定义：%s
            待评审假设：%s
            只输出一个 JSON：{"score": 整数}
            """;

    private final CitationVerifier citationVerifier;
    private final BailianClient bailianClient;
    /** 调试模式（RAG_MOCK_SAMPLES=true）：放宽打回红线（不因无引用/核验不过中断），仅评分；生产保持严格 */
    private final boolean mockSamples;

    public EvaluationStage(CitationVerifier citationVerifier, BailianClient bailianClient,
                           @Value("${vector.mock-samples:false}") boolean mockSamples) {
        this.citationVerifier = citationVerifier;
        this.bailianClient = bailianClient;
        this.mockSamples = mockSamples;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.EVALUATION;
    }

    @Override
    public void execute(PipelineContext ctx) {
        PipelineModels.HypothesisResult hypotheses = ctx.getHypothesis();
        if (hypotheses == null || hypotheses.hypotheses().isEmpty()) {
            throw new IllegalStateException("评估阶段需要候选假设（④ 假设生成输出）");
        }

        List<PipelineModels.Hypothesis> cands = hypotheses.hypotheses();
        List<PipelineModels.ScoredHypothesis> rankings = new ArrayList<>();
        List<PipelineModels.HallucinationCheck> hallucinationReport = new ArrayList<>();
        List<String> verifiedReferences = new ArrayList<>();

        for (PipelineModels.Hypothesis h : cands) {
            double innovation = llmScore("创新性", "是否提出研究局限/空白，体现原创思想或跨学科迁移", h.summary() + " " + h.rationale());
            double feasibility = llmScore("可行性", "是否给出可验证路径（基线、指标、技术栈）", String.join(" ", h.methods()) + " " + String.join(" ", h.technicalDetails()));

            double citationReliability = 1.0;
            boolean hallucinated = false;
            int verifiedCount = 0;
            int unverifiableCount = 0;

            List<String> citations = collectCitations(h);
            if (citations.isEmpty()) {
                citationReliability = 0.0;
                hallucinationReport.add(new PipelineModels.HallucinationCheck(
                        "（无引用）", false, "缺少可核验的真实文献引用"));
            } else {
                for (String citation : citations) {
                    CitationCheck check = citationVerifier.verify(citation);
                    boolean ok = check.status() == CitationStatus.VERIFIED;
                    hallucinationReport.add(new PipelineModels.HallucinationCheck(
                            citation, ok, check.note()));
                    if (ok) {
                        verifiedCount++;
                        if (check.matchedTitle() != null) {
                            verifiedReferences.add("doi:" + safe(citation));
                        }
                    } else if (check.status() == CitationStatus.NOT_FOUND
                            || check.status() == CitationStatus.SUSPICIOUS) {
                        hallucinated = true;
                    } else {
                        unverifiableCount++;
                    }
                }
                citationReliability = verifiedCount * 1.0 / citations.size();
                if (hallucinated) {
                    citationReliability = Math.min(citationReliability, 0.3);
                }
            }

            double dataAvailability = 0.6; // 数据集可得性由数据引擎 + 评估共同把关，此处默认中位值

            double overall = innovation * W_INNOVATION + feasibility * W_FEASIBILITY
                    + citationReliability * W_CITATION + dataAvailability * W_DATA;

            // 打回红线：任一命中即拒绝通过（调试模式放宽：不中断，仅评分）
            if (!mockSamples) {
                if (citations.isEmpty()) {
                    throw new IllegalStateException("评估未通过：候选假设缺少可核验的真实文献引用");
                }
                if (verifiedCount == 0) {
                    throw new IllegalStateException("评估未通过：至少需一条真实引用核验通过");
                }
                if (hallucinated) {
                    throw new IllegalStateException("评估未通过：检出虚构/存疑引用，必须打回重做");
                }
                if (unverifiableCount > 0) {
                    throw new IllegalStateException("评估未通过：存在 " + unverifiableCount + " 条无法核验的引用，请稍后重试或人工确认");
                }
            }

            rankings.add(new PipelineModels.ScoredHypothesis(
                    h.summary(),
                    innovation,
                    feasibility,
                    citationReliability,
                    dataAvailability,
                    overall));
        }

        ctx.setEvaluation(new PipelineModels.EvaluationResult(
                List.copyOf(rankings),
                List.copyOf(hallucinationReport),
                List.copyOf(verifiedReferences)));
    }

    /** 从假设中收集引用：优先 evidenceIds（DOI/PMID/URL），回退 reasoningChain 中提取的引用串 */
    private List<String> collectCitations(PipelineModels.Hypothesis h) {
        List<String> out = new ArrayList<>();
        if (h.evidenceIds() != null) {
            out.addAll(h.evidenceIds());
        }
        if (h.reasoningChain() != null) {
            for (String step : h.reasoningChain()) {
                String doi = CitationVerifier.extractDoi(step);
                String arxiv = CitationVerifier.extractArxiv(step);
                String pmid = CitationVerifier.extractPmid(step);
                if (doi != null) {
                    out.add(doi);
                } else if (arxiv != null) {
                    out.add("arXiv:" + arxiv);
                } else if (pmid != null) {
                    out.add("pmid:" + pmid);
                }
            }
        }
        return out.stream().distinct().toList();
    }

    /** 调用 Qwen 评分；无 Key 或解析失败回退启发式，保证流程不中断 */
    private double llmScore(String dimension, String definition, String hypothesis) {
        try {
            String reply = bailianClient.chat("qwen-max", "你是科学假设评审专家", SCORING_PROMPT.formatted(dimension, definition, abbreviate(hypothesis)));
            Matcher m = SCORE_PATTERN.matcher(reply);
            if (m.find()) {
                return Math.max(0, Math.min(100, Double.parseDouble(m.group(1)))) / 100.0;
            }
        } catch (Exception e) {
            // 回退启发式
        }
        return heuristicScore(dimension, hypothesis);
    }

    private double heuristicScore(String dimension, String hypothesis) {
        String t = hypothesis == null ? "" : hypothesis.toLowerCase();
        double base = 0.5;
        if ("创新性".equals(dimension)) {
            if (t.contains("首次") || t.contains("创新") || t.contains("gap") || t.contains("空白") || t.contains("跨学科")) {
                base += 0.2;
            }
        } else {
            if (t.contains("baseline") || t.contains("基线") || t.contains("指标") || t.contains("metric") || t.contains("accuracy") || t.contains("f1")) {
                base += 0.2;
            }
        }
        return Math.min(1.0, base);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 800 ? text : text.substring(0, 800);
    }

    private static String safe(String citation) {
        String trimmed = citation == null ? "" : citation.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("doi:")) {
            return trimmed.substring(4);
        }
        return trimmed;
    }
}
