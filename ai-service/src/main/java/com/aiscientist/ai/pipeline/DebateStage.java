package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.llm.BailianClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * ⑦ 思辨辩论阶段接入适配器（钱思妤负责后端；前端吴浩瑜 Vue Flow 可视化）。
 *
 * <p>实现 {@link PipelineAgent}：从 {@code ctx.getEvaluation()} 读评估结果，
 * 以「倡议者」「质疑者」两角色用 Qwen 多轮辩论，写入 {@code ctx.setDebate()}。</p>
 *
 * <p>未配置百炼 Key 时回退模板发言，保证管线可跑通。</p>
 */
@Component
public class DebateStage implements PipelineAgent {

    private static final String PROPOSER_SYSTEM = "你是科研团队中的「倡议者」，坚定地为这条科学假设辩护，语言简洁有力。";
    private static final String SKEPTIC_SYSTEM = "你是科研团队中的「质疑者」，以批判视角审视假设，聚焦逻辑漏洞与引用风险。";

    private static final String PROPOSER_PROMPT = "请以「倡议者」身份开题陈述这条假设的创新点与可行性（150字内）：%s";
    private static final String SKEPTIC_PROMPT = "请以「质疑者」身份质疑这条假设的逻辑漏洞与引用风险（150字内）：%s";
    private static final String REBUTTAL_PROMPT = "针对质疑「%s」，请以「倡议者」身份回应补充论证（100字内）。";

    private final BailianClient bailianClient;

    public DebateStage(BailianClient bailianClient) {
        this.bailianClient = bailianClient;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.DEBATE;
    }

    @Override
    public void execute(PipelineContext ctx) {
        PipelineModels.EvaluationResult evaluation = ctx.getEvaluation();
        if (evaluation == null || evaluation.rankings().isEmpty()) {
            throw new IllegalStateException("辩论阶段需要评估输出（⑤ 评估阶段产物）");
        }

        PipelineModels.ScoredHypothesis best = evaluation.rankings().get(0);
        List<String> debateLog = new ArrayList<>();

        String proposer = generate(PROPOSER_SYSTEM, PROPOSER_PROMPT.formatted(best.summary()));
        debateLog.add("倡议者：" + proposer);

        String skeptic = generate(SKEPTIC_SYSTEM, SKEPTIC_PROMPT.formatted(best.summary()));
        debateLog.add("质疑者：" + skeptic);

        String rebuttal = generate(PROPOSER_SYSTEM, REBUTTAL_PROMPT.formatted(skeptic));
        debateLog.add("倡议者（回应）：" + rebuttal);

        ctx.setDebate(new PipelineModels.DebateResult(
                List.copyOf(debateLog),
                refinedComments(best, evaluation)));
    }

    private String generate(String system, String user) {
        try {
            String reply = bailianClient.chat("qwen-max", system, user);
            return (reply == null || reply.isBlank()) ? fallback(user) : reply.trim();
        } catch (Exception e) {
            return fallback(user);
        }
    }

    private String fallback(String user) {
        if (user.contains("质疑者")) {
            return "质疑者：假设缺少充分证据与可验证路径，需补充真实引用。";
        }
        return "倡议者：本假设具备创新性与可行性，将补充证据支撑。";
    }

    private String refinedComments(PipelineModels.ScoredHypothesis best, PipelineModels.EvaluationResult evaluation) {
        StringBuilder sb = new StringBuilder();
        sb.append("经辩论，假设「").append(best.summary()).append("」");
        if (evaluation.hallucinationReport().stream().anyMatch(h -> !h.verified())) {
            sb.append("存在引用疑点，需回退评估环节核验；");
        } else {
            sb.append("论证自洽，可进入下一阶段；");
        }
        sb.append("综合评分 ").append(String.format("%.2f", best.overall())).append("。");
        return sb.toString();
    }
}
