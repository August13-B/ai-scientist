package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;

import java.util.List;

/**
 * 各阶段数据契约（集中定义，供队友接入时对照）。
 *
 * <p>规则：所有引用了文献的字段必须携带可溯源来源标识
 * （DOI/PMID/URL，见 {@link PaperEvidence}），严禁虚构。</p>
 *
 * <p>字段含义依据 docs/agents.md 各 Agent 职责定义；具体实现由各 Agent 负责人完成。</p>
 */
public final class PipelineModels {

    private PipelineModels() {
    }

    // ==================== ① 问题理解 Agent 输出 ====================

    /** 结构化子查询：领域标签 / 关键概念 / 已知条件 / 待求解变量 */
    public record QuestionQuery(
            String originalQuestion,
            String domain,
            List<String> subQueries,
            List<String> keyConcepts,
            List<String> knownConditions,
            List<String> targetVariables
    ) {
        public QuestionQuery {
            originalQuestion = requireText(originalQuestion, "originalQuestion");
            subQueries = immutable(subQueries);
            keyConcepts = immutable(keyConcepts);
            knownConditions = immutable(knownConditions);
            targetVariables = immutable(targetVariables);
        }
    }

    // ==================== ② 文献检索 Agent 输出 ====================

    /** 召回文献（复用 PaperEvidence 契约）+ 关键发现 + 引用链 */
    public record LiteratureResult(
            List<PaperEvidence> papers,
            List<String> keyFindings,
            List<String> citationChains
    ) {
        public LiteratureResult {
            papers = immutable(papers);
            keyFindings = immutable(keyFindings);
            citationChains = immutable(citationChains);
        }
    }

    // ==================== ③ 知识发现 Agent 输出 ====================

    // 复用马艺萌已实现的 DiscoveryResult（见 KnowledgeDiscoveryModels），
    // 包含 researchGaps / selectedProblem / paperTitle / paperAbstract / references 等。

    // ==================== ④ 假设生成 Agent 输出 ====================

    /** 单个候选假设：结论 + 解决思路 + 技术手段 + 方法论 + 推理链 + 引用证据 */
    public record Hypothesis(
            String summary,
            String rationale,
            List<String> technicalDetails,
            List<String> methods,
            List<String> reasoningChain,
            List<String> evidenceIds
    ) {
        public Hypothesis {
            summary = requireText(summary, "summary");
            rationale = requireText(rationale, "rationale");
            technicalDetails = immutable(technicalDetails);
            methods = immutable(methods);
            reasoningChain = immutable(reasoningChain);
            evidenceIds = immutable(evidenceIds);
        }
    }

    /** 候选假设列表（3–5 个） */
    public record HypothesisResult(List<Hypothesis> hypotheses) {
        public HypothesisResult {
            hypotheses = immutable(hypotheses);
        }
    }

    // ==================== ⑤ 科学假设评估 Agent 输出 ====================

    /** 单条假设评分：创新性 / 可行性 / 引用真实性 / 数据可获得性 */
    public record ScoredHypothesis(
            String summary,
            double innovation,
            double feasibility,
            double citationReliability,
            double dataAvailability,
            double overall
    ) {
        public ScoredHypothesis {
            summary = requireText(summary, "summary");
            if (overall < 0 || overall > 1) {
                throw new IllegalArgumentException("overall must be between 0 and 1");
            }
        }
    }

    /** 幻觉检测单条记录：每条引用必须反向比对真实文献 */
    public record HallucinationCheck(
            String citation,
            boolean verified,
            String note
    ) {
        public HallucinationCheck {
            citation = requireText(citation, "citation");
        }
    }

    /** 评分排序 + 幻觉检测报告 + 最终真实 References */
    public record EvaluationResult(
            List<ScoredHypothesis> rankings,
            List<HallucinationCheck> hallucinationReport,
            List<String> references
    ) {
        public EvaluationResult {
            rankings = immutable(rankings);
            hallucinationReport = immutable(hallucinationReport);
            references = immutable(references);
        }
    }

    // ==================== ⑥ 实验设计 Agent 输出 ====================

    /** 实验方案：Baselines + Metrics + 拟用数据集 + 预期结果 */
    public record ExperimentResult(
            List<String> baselines,
            List<String> metrics,
            List<String> datasets,
            String expectedResults
    ) {
        public ExperimentResult {
            baselines = immutable(baselines);
            metrics = immutable(metrics);
            datasets = immutable(datasets);
        }
    }

    // ==================== 人在回路（Human-in-the-Loop）====================

    /**
     * 人类审阅意见（暂停点 resume 时提交）。
     * <p>语义：人类在 ④ 假设生成后审阅候选假设，可仅确认（两字段皆空）、
     * 可附审阅意见、可提交修改后的候选假设列表（替换或追加到 ④ 输出，
     * 供 ⑤ 评估与 ⑦ 辩论消费）。字段均为可选。</p>
     */
    public record HumanFeedback(
            String reviewComment,
            List<Hypothesis> revisedHypotheses
    ) {
        public HumanFeedback {
            reviewComment = hasText(reviewComment) ? reviewComment.trim() : null;
            revisedHypotheses = immutable(revisedHypotheses);
        }

        public boolean isEmpty() {
            return reviewComment == null && revisedHypotheses.isEmpty();
        }
    }

    // ==================== ⑦ 思辨辩论 Agent 输出 ====================

    /** 辩论纪要 + 对研究计划的完善意见 */
    public record DebateResult(
            List<String> debateLog,
            String refinedComments
    ) {
        public DebateResult {
            debateLog = immutable(debateLog);
        }
    }

    // ==================== 工具方法（不可变 + 非空校验） ====================

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
