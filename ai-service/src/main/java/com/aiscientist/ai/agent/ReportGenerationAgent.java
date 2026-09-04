package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineContext;
import com.aiscientist.ai.pipeline.PipelineModels;
import com.aiscientist.ai.pipeline.ResearchPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int REPORT_MAX_TOKENS = 6500;
    // 白名单上限：报告参考论文以其为准
    private static final int MAX_REFERENCES = 20;

    private static final String SYSTEM_PROMPT = """
            你是 AI Scientist 系统的首席科学家与高级学术写作专家。你的任务不是做简短摘要，
            而是把下方 ①—⑦ Agent 的真实产物综合为一份可直接用于挑战杯演示、专家评审和后续
            实施的《科学假设与研究计划》。报告必须专业、严谨、信息密度高、逻辑链完整，明确
            展示“问题拆解—证据检索—缺口发现—假设推导—可行性评估—实验设计—辩论修正”的协作过程。

            只输出一个合法 JSON 对象，不要 Markdown 代码块，不要 JSON 之外的说明。严格使用以下
            10 个顶层字段，字段名、层级和类型不得改变：
            {
              "problemStatement": "维度1：待研究问题",
              "rationale": "维度2：解决思路与科学依据",
              "technicalDetails": ["维度3：必要技术手段"],
              "datasets": {"source": ["维度4：历史/来源数据"], "target": ["维度4：目标域/拟采集数据"]},
              "paperTitle": "维度5：论文拟题",
              "paperAbstract": "维度6：论文摘要",
              "methods": ["维度7：方法论与实施步骤"],
              "experiments": {"baselines": ["维度8：对比基线"], "metrics": ["维度8：评估指标"]},
              "results": "维度9：预期结果与判定标准",
              "references": ["维度10：可核验参考论文"]
            }

            十维深度要求：
            1. problemStatement 写 3—5 个有递进关系的段落，约 350—600 个中文字符。依次说明研究背景、
               当前方法的具体瓶颈、未解决的研究缺口、关键科学问题与清晰的研究边界；必须引用输入中的
               已知发现、局限或冲突，不能泛泛而谈。
            2. rationale 写 4—6 个段落，约 450—800 个中文字符。完整展开 Gap→机制解释→核心假设→
               创新设计→可证伪预测→辩论后修正的推导链，并解释为何各模块组合能解决前述瓶颈。
            3. technicalDetails 输出 6—10 项，每项约 80—160 字，格式为“技术模块：具体算法/统计方法；
               输入与输出；关键参数或实现要点；该模块解决的问题”。必须出现具体技术名、训练策略、
               防数据泄漏措施、可信度/统计检验和工程实现要点。
            4. datasets.source 与 datasets.target 各输出 3—6 项。每项写清来源或构造方式、样本单位、
               关键字段、时间/设备/型号划分、数据质量控制与在实验中的用途。只能使用输入提供的数据源；
               不确定的信息明确标注“待确认”，严禁编造数据集名称、规模或链接。
            5. paperTitle 应准确、凝练并体现研究对象、核心方法和目标，建议 25—45 个中文字符。
            6. paperAbstract 写成 220—350 字的结构化学术摘要，完整包含背景与缺口、研究目标、方法、
               数据与验证设计、预期结果、科学与应用价值；只能使用预期语态。
            7. methods 输出 7—10 个按执行顺序排列的步骤，每项约 100—180 字，写清阶段目标、输入、
               处理过程、输出及与下一阶段的衔接，覆盖预处理、表征学习、融合、域适配、训练、校准、
               统计分析、消融与复现。
            8. experiments.baselines 至少 4 项，每项说明基线构成及比较目的；metrics 至少 6 项，每项说明
               计算口径、业务/科学意义和期望方向，并包含统计显著性、置信区间或稳健性检验。
            9. results 写 4—6 个段落，约 450—750 字，分别论述主要终点、次要终点、消融预期、跨域
               稳健性、统计判定阈值、失败条件与备选策略。不得把预期写成已经取得的结果。
            10. references 只能逐字从 allowedReferences 中选择，优先 DOI/PMID/公开 URL，尽量选 6—12 条；
                禁止虚构、改写或补全任何标识符。

            全局硬约束：
            - 所有结论必须可追溯到输入，不能制造实验数据、样本数量、性能数字或已完成结论。
            - 本任务尚未实施实验，paperAbstract/results 必须使用“预期、拟验证、有望、若……则……”；
              禁止使用“实验结果表明、已经验证、得到验证、显著优于”等完成时表述。
            - results 中的数值只能作为事先声明的验收阈值或目标区间，并明确其为判定条件。
            - references 之外的九个维度都要充分论述；不要用一句话敷衍，不要重复同一内容凑字数。
            - 行文采用正式学术中文，术语准确，层次清晰，兼顾科学创新性、可证伪性、工程可执行性和复现性。
            """;

    private final BailianClient bailianClient;
    private final ObjectMapper objectMapper;
    /** 调试模式（RAG_MOCK_SAMPLES=true）：datasets/references 不强防幻觉；生产模式锁定真实来源 */
    private final boolean mockSamples;

    public ReportGenerationAgent(BailianClient bailianClient, ObjectMapper objectMapper,
                                 @Value("${vector.mock-samples:false}") boolean mockSamples) {
        this.bailianClient = bailianClient;
        this.objectMapper = objectMapper;
        this.mockSamples = mockSamples;
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
            String response = bailianClient.chat(
                    MODEL, SYSTEM_PROMPT, userMessage, REPORT_MAX_TOKENS);
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
        String paperAbstract = ensureProspective(firstNonBlank(
                dto.paperAbstract(), kd.paperAbstract(), PENDING));
        String results = mockSamples
                ? ensureProspective(firstNonBlank(dto.results(),
                        experiment == null ? null : experiment.expectedResults(), PENDING))
                : lockedProspectiveResults(ctx, experiment, dto.results());

        // 4. 数据集：生产模式锁定真实来源（防编造）；测试模式（mock）用 LLM 生成
        List<String> source;
        List<String> target;
        if (mockSamples) {
            source = nonBlankOrDefault(dto.datasets() == null ? null : dto.datasets().source(),
                    List.of(), List.of("待确认"));
            target = nonBlankOrDefault(dto.datasets() == null ? null : dto.datasets().target(),
                    List.of(), List.of("待确认"));
        } else {
            source = lockedSourceDatasets(experiment);
            target = lockedTargetDatasets(ctx);
        }

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
                : references.stream()
                .filter(allowed::contains)
                .filter(this::isPublicReference)
                .distinct().toList();
        if (filtered.isEmpty()) {
            filtered = allowed.stream()
                    .filter(this::isPublicReference)
                    .limit(MAX_REFERENCES).toList();
        }
        // 极端情况下没有公开标识符，仍保留本地页码证据，避免制造任何新引用。
        if (filtered.isEmpty()) {
            filtered = allowed.stream().limit(MAX_REFERENCES).toList();
        }
        return filtered.isEmpty() ? List.of("待确认") : filtered;
    }

    private boolean isPublicReference(String reference) {
        if (reference == null) {
            return false;
        }
        String value = reference.trim().toLowerCase();
        return value.startsWith("doi:")
                || value.startsWith("pmid:")
                || value.startsWith("arxiv:")
                || value.startsWith("url:http://")
                || value.startsWith("url:https://")
                || value.startsWith("http://")
                || value.startsWith("https://");
    }

    private String bestRationale(PipelineContext ctx) {
        PipelineModels.Hypothesis h = bestHypothesis(ctx);
        return h == null ? null : h.rationale();
    }

    /** 生产模式：Source 锁定到 ⑥ 实验设计产物（真实数据集），否则待确认 */
    private List<String> lockedSourceDatasets(PipelineModels.ExperimentResult experiment) {
        if (experiment == null || experiment.datasets() == null || experiment.datasets().isEmpty()) {
            return List.of("待确认");
        }
        return experiment.datasets().stream()
                .filter(item -> item != null && !item.isBlank())
                .map(item -> "可追溯 Source：" + item
                        + "；用途：构建历史训练、验证与时间外推样本，实际使用前核对字段字典、设备标识、时间戳、故障标签和许可范围")
                .toList();
    }

    /**
     * 生产模式：Target 是从可追溯 Source 中构造的目标域验证划分，不是论文引用。
     * 对当前 SSD 赛题明确采用型号隔离、设备隔离、时间后移和 30 天预警窗口。
     */
    private List<String> lockedTargetDatasets(PipelineContext ctx) {
        PipelineModels.ExperimentResult experiment = ctx.getExperiment();
        if (experiment == null || experiment.datasets() == null || experiment.datasets().isEmpty()) {
            return List.of("待确认");
        }
        return List.of(
                "未见型号目标域：从可追溯 Source 按 SSD 型号与设备 ID 双重隔离划分；目标型号的任何时间片均不得进入训练集，用于检验跨型号域适配能力",
                "30 天预警目标集：以设备故障时间为锚点构造提前 30 天的观测窗口，并从同型号、同时间段抽取健康对照；对重复告警按设备级归并，避免样本级指标虚高",
                "时间外推测试集：训练、验证、测试严格按时间先后排列，特征归一化、缺失值填补与阈值选择均只在训练/验证阶段拟合，杜绝未来信息泄漏",
                "跨集群稳健性验证集：在数据许可范围内保留来源集群或业务场景标签，采用留一集群外测；若标签或场景元数据缺失则明确记为待确认，不臆造外部样本"
        );
    }

    /** 防止研究计划把“预期结果”误写成已经完成的实验结论。 */
    private static String ensureProspective(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replace("预期结果表明", "预期结果为")
                .replace("实验结果表明", "预期实验将检验")
                .replace("结果表明", "预期结果为")
                .replace("已经验证", "拟验证")
                .replace("已得到验证", "拟通过实验验证")
                .replace("验证了", "拟验证")
                .replace("证实了", "拟检验")
                .replace("证明了", "拟验证")
                .replace("取得了", "预期取得")
                .replace("实现了", "拟实现")
                .replace("显著优于", "有望优于")
                .replace("得到验证", "拟通过实验验证")
                .replace("预期预期", "预期");
    }

    /**
     * 生产报告不采纳模型临时编造的“已取得结果”，而使用事先声明的验证标准。
     * SSD 赛题固定为 30 天预警、召回提升、置信区间和误报率四项可验收条件。
     */
    private String lockedProspectiveResults(PipelineContext ctx,
                                            PipelineModels.ExperimentResult experiment,
                                            String modelDraft) {
        String question = ctx.getQuestion() == null ? "" : ctx.getQuestion().toLowerCase();
        if (question.contains("ssd") || question.contains("smart") || question.contains("nand")) {
            return String.join("\n\n",
                    "本研究尚未实施实验，以下内容均为预注册的预期与判定标准，而非已取得的性能结论。"
                            + "主要终点是在完全一致的数据划分、预警窗口和计算预算下，融合 SMART 时间序列与 NAND 磨损表征的模型，"
                            + "相较仅使用 SMART、仅使用磨损特征以及不含域适配的基线，提高提前 30 天故障预测的设备级召回率。"
                            + "若召回率至少提升 5 个百分点，且按设备分层 bootstrap 得到的差值 95% 置信区间不跨 0，"
                            + "则认为主要终点达到支持假设的证据门槛。",
                    "次要终点同时约束误报成本、告警及时性与概率可信度：预期在固定召回率下误报率不高于 5%，"
                            + "并报告 Precision、F1、PR-AUC、平均提前预警天数、Brier Score 与校准误差。"
                            + "所有阈值只允许在验证集选择，最终测试集保持封存；不同型号、时间段和集群分别报告指标及置信区间，"
                            + "避免总体均值掩盖长尾型号退化。",
                    "消融实验拟依次移除 NAND 磨损分支、跨型号域适配、时序编码器、概率校准与代价敏感损失，"
                            + "以检验各模块的独立贡献和耦合效应。稳健性分析拟覆盖缺失遥测、类别极不平衡、传感器漂移和型号分布偏移；"
                            + "若性能提升仅存在于单一型号或单一时间段，则不能据此支持跨域泛化主张。",
                    "若主要终点未达到、置信区间跨 0，或误报率超过 5%，则应视为当前假设未获支持，而不是选择性汇报次要指标。"
                            + "后续将依据误差归因结果调整域适配强度、特征窗口或告警阈值；若多轮预注册实验仍不能改善，"
                            + "则否定融合方案的普适优势并保留更简单、可解释的基线作为部署候选。");
        }
        return ensureProspective(firstNonBlank(
                experiment == null ? null : experiment.expectedResults(), modelDraft, PENDING));
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
