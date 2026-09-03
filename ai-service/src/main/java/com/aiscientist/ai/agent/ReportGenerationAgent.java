package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineContext;
import com.aiscientist.ai.pipeline.PipelineModels;
import com.aiscientist.ai.pipeline.ResearchPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ⑧ 报告生成 Agent：融合 ①-⑦ 全部 Agent 产出，输出最终《科学假设与研究计划》。
 *
 * <p>与单 Agent 直出的区别：输入包含各环节的<b>过程性推理</b>——① 子查询/关键概念、
 * ② keyFindings/citationChains、③ researchGaps、④ reasoningChain、⑤ 评分/幻觉报告、
 * ⑥ 实验方案、⑦ 辩论纪要，LLM 据此生成一份<b>多 Agent 协作链路清晰</b>的 10 字段报告。</p>
 *
 * <p>红线：references 仅接受真实引用白名单（{@link PipelineModels.EvaluationResult#references()}
 * ⑤ 核验通过 ∪ {@link #knowledgeDiscovery} 溯源），禁止虚构；生成后逐字段校验，缺失/空
 * 时用对应阶段产物兜底（保底不缺字段），最终回退 {@link ResearchPlanAssembler} 装配。</p>
 */
@Service
public class ReportGenerationAgent {

    private static final String MODEL = "qwen-max";
    // 白名单上限：报告参考论文以其为准
    private static final int MAX_REFERENCES = 20;

    private static final String SYSTEM_PROMPT = """
            你是科学假设研究计划撰写 Agent。请基于下方「多 Agent 协作产物」，产出最终
            《科学假设与研究计划》——必须体现各 Agent 的推理与协作：问题如何被拆解（①）、
            文献如何支撑（②）、研究缺口从何而来（③）、假设如何推理得出（④）、如何被评估
            （⑤）、怎么设计实验验证（⑥）、经受了怎样的正反辩论（⑦）。

            强制规则：
            1. 只输出一个 JSON 对象（不要 markdown 代码块、不要解释文字），结构如下，每个字段必须用中文填写：
            {
              "problemStatement": "待研究问题",
              "rationale": "解决思路（融入 ④ 假设的推理与 ⑦ 辩论共识）",
              "technicalDetails": ["必要技术手段"],
              "datasets": {"source": ["历史/来源数据"], "target": ["验证拟采集数据"]},
              "paperTitle": "标题",
              "paperAbstract": "摘要（≤200字）",
              "methods": ["方法论"],
              "experiments": {"baselines": ["对比基线"], "metrics": ["评估指标"]},
              "results": "预期结果（含范围/判定条件，融入 ⑦ 辩论后的完善）",
              "references": ["doi:...或pmid:...或url:..."]
            }
            2. references 只能从下方 allowedReferences 中选择，禁止虚构任何文献、来源、数据；
            3. 所有字段必须非空，datasets 的 source/target 若空缺用「待确认」；
            4. 结论要明确：这是一个多智能体协作得出的可执行研究方案。
            """;

    private final BailianClient bailianClient;
    private final ObjectMapper objectMapper;

    public ReportGenerationAgent(BailianClient bailianClient, ObjectMapper objectMapper) {
        this.bailianClient = bailianClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成最终 10 字段报告。
     *
     * @param ctx 管线上下文（含 ①-⑦ 产物）
     * @return 10 字段 {@link ResearchPlan}
     * @throws IllegalStateException LLM 返回无效 JSON 时抛出（调用方可回退 assembler）
     */
    public ResearchPlan generate(PipelineContext ctx) {
        Set<String> allowed = allowedReferences(ctx);
        Map<String, Object> payload = buildPayload(ctx, allowed);

        ReportDto dto;
        try {
            String userMessage = objectMapper.writeValueAsString(payload);
            String response = bailianClient.chat(MODEL, SYSTEM_PROMPT, userMessage);
            dto = objectMapper.readValue(stripCodeFence(response), ReportDto.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("报告生成 Agent 返回了无效 JSON", exception);
        }
        return toResearchPlan(dto, ctx, allowed);
    }

    // ==================== 白名单与 payload ====================

    /** references 白名单：⑤ 核验通过的真实引用 ∪ ③ 知识发现溯源 */
    private Set<String> allowedReferences(PipelineContext ctx) {
        Set<String> allowed = new LinkedHashSet<>();
        PipelineModels.EvaluationResult evaluation = ctx.getEvaluation();
        if (evaluation != null && evaluation.references() != null) {
            allowed.addAll(evaluation.references());
        }
        if (ctx.getKnowledgeDiscovery() != null
                && ctx.getKnowledgeDiscovery().references() != null) {
            allowed.addAll(ctx.getKnowledgeDiscovery().references());
        }
        return allowed;
    }

    private Map<String, Object> buildPayload(PipelineContext ctx, Set<String> allowed) {
        // 多 Agent 协作过程性产物作为输入（体现与单 Agent 直出的区别）
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("question", ctx.getQuestion());
        payload.put("domain", domainOf(ctx));
        payload.put("problemUnderstanding", ctx.getQuestionQuery());
        payload.put("literatureRetrieval", ctx.getLiterature());
        payload.put("knowledgeDiscovery", ctx.getKnowledgeDiscovery());
        payload.put("hypothesis", ctx.getHypothesis());
        payload.put("evaluation", ctx.getEvaluation());
        payload.put("experiment", ctx.getExperiment());
        payload.put("debate", ctx.getDebate());
        payload.put("allowedReferences", List.copyOf(allowed));
        return payload;
    }

    private String domainOf(PipelineContext ctx) {
        return ctx.getQuestionQuery() == null || ctx.getQuestionQuery().domain() == null
                ? "通用科研" : ctx.getQuestionQuery().domain();
    }

    // ==================== DTO 与映射 ====================

    /** LLM 输出 DTO（字段可缺失，缺后用阶段产物兜底） */
    private record ReportDto(
            String problemStatement,
            String rationale,
            List<String> technicalDetails,
            DatasetDto datasets,
            String paperTitle,
            String paperAbstract,
            List<String> methods,
            ExperimentsDto experiments,
            String results,
            List<String> references
    ) {
    }

    private record DatasetDto(List<String> source, List<String> target) {
    }

    private record ExperimentsDto(List<String> baselines, List<String> metrics) {
    }

    /** DTO → ResearchPlan：字段缺失/空白用对应阶段产物兜底；references 用白名单过滤 */
    private ResearchPlan toResearchPlan(ReportDto dto, PipelineContext ctx, Set<String> allowed) {
        PipelineModels.EvaluationResult evaluation = ctx.getEvaluation();
        PipelineModels.ExperimentResult experiment = ctx.getExperiment();
        DiscoveryResultWrapper kd = new DiscoveryResultWrapper(ctx.getKnowledgeDiscovery());

        String problemStatement = firstNonBlank(dto.problemStatement(),
                kd.selectedProblem(), ctx.getQuestion(), PENDING);
        String rationale = firstNonBlank(dto.rationale(),
                bestRationale(ctx), PENDING);
        List<String> technicalDetails = nonBlankOrDefault(dto.technicalDetails(),
                bestTechnicalDetails(ctx), List.of(PENDING));
        List<String> methods = nonBlankOrDefault(dto.methods(),
                bestMethods(ctx), List.of(PENDING));
        String paperTitle = firstNonBlank(dto.paperTitle(), kd.paperTitle(), PENDING);
        String paperAbstract = firstNonBlank(dto.paperAbstract(), kd.paperAbstract(), PENDING);
        String results = firstNonBlank(dto.results(),
                experiment == null ? null : experiment.expectedResults(), PENDING);

        // 4. 数据集：LLM 给出则用；缺失回退 ⑥ / 待确认
        List<String> source = nonBlankOrDefault(dto.datasets() == null ? null : dto.datasets().source(),
                List.of(), List.of("待确认"));
        List<String> target = nonBlankOrDefault(dto.datasets() == null ? null : dto.datasets().target(),
                List.of(), List.of("待确认"));

        // 8. 实验设计
        List<String> baselines = nonBlankOrDefault(
                dto.experiments() == null ? null : dto.experiments().baselines(),
                experiment == null ? List.of() : experiment.baselines(), List.of(PENDING));
        List<String> metrics = nonBlankOrDefault(
                dto.experiments() == null ? null : dto.experiments().metrics(),
                experiment == null ? List.of() : experiment.metrics(), List.of(PENDING));

        // 10. 参考论文：白名单过滤 + 非空兜底（赛题严禁虚构）
        List<String> references = sanitizeReferences(dto.references(), allowed, ctx);

        return new ResearchPlan(
                problemStatement,
                rationale,
                technicalDetails,
                new ResearchPlan.DatasetPlan(source, target),
                paperTitle,
                paperAbstract,
                methods,
                new ResearchPlan.ExperimentPlan(baselines, metrics),
                results,
                references
        );
    }

    /** references：过滤掉白名单外，非空；空则回退白名单（取前 MAX_REFERENCES） */
    private List<String> sanitizeReferences(List<String> references, Set<String> allowed,
                                            PipelineContext ctx) {
        List<String> filtered = references == null ? List.of()
                : references.stream().filter(allowed::contains).distinct().toList();
        if (filtered.isEmpty()) {
            filtered = allowed.stream().limit(MAX_REFERENCES).toList();
        }
        return filtered.isEmpty() ? List.of("待确认") : filtered;
    }

    private String bestRationale(PipelineContext ctx) {
        PipelineModels.Hypothesis h = bestHypothesis(ctx);
        return h == null ? null : h.rationale();
    }

    private List<String> bestTechnicalDetails(PipelineContext ctx) {
        PipelineModels.Hypothesis h = bestHypothesis(ctx);
        return h == null ? List.of() : h.technicalDetails();
    }

    private List<String> bestMethods(PipelineContext ctx) {
        PipelineModels.Hypothesis h = bestHypothesis(ctx);
        return h == null ? List.of() : h.methods();
    }

    /** 最优假设：评估 rankings 第一，缺失回退假设列表首个 */
    private PipelineModels.Hypothesis bestHypothesis(PipelineContext ctx) {
        PipelineModels.HypothesisResult result = ctx.getHypothesis();
        if (result == null || result.hypotheses().isEmpty()) {
            return null;
        }
        PipelineModels.EvaluationResult evaluation = ctx.getEvaluation();
        if (evaluation != null && !evaluation.rankings().isEmpty()) {
            String bestSummary = evaluation.rankings().get(0).summary();
            return result.hypotheses().stream()
                    .filter(item -> item.summary().equals(bestSummary))
                    .findFirst().orElse(result.hypotheses().get(0));
        }
        return result.hypotheses().get(0);
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private static List<String> nonBlankOrDefault(List<String> value, List<String> fallback,
                                                  List<String> placeholder) {
        if (value != null && !value.isEmpty() && value.stream().allMatch(
                item -> item != null && !item.isBlank())) {
            return List.copyOf(value);
        }
        if (fallback != null && !fallback.isEmpty()) {
            return List.copyOf(fallback);
        }
        return List.copyOf(placeholder);
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

    private static final String PENDING = "待生成（对应阶段 Agent 接入后填充）";

    /** 轻量包装，避免直接依赖 DiscoveryResult 全类型 */
    private record DiscoveryResultWrapper(
            String selectedProblem, String paperTitle, String paperAbstract,
            List<String> references) {
        DiscoveryResultWrapper(com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult kd) {
            this(kd == null ? null : kd.selectedProblem(),
                    kd == null ? null : kd.paperTitle(),
                    kd == null ? null : kd.paperAbstract(),
                    kd == null ? List.of() : kd.references());
        }
    }
}
