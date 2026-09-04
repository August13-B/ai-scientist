package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.ReportGenerationAgent;
import org.springframework.stereotype.Component;

/**
 * ⑧ 报告生成阶段接入适配器。
 *
 * <p>实现 {@link PipelineAgent}（stage=REPORT）：读取 ①-⑦ 全部产物，调用
 * {@link ReportGenerationAgent} 生成最终 10 字段《科学假设与研究计划》，写入
 * {@code ctx.setFinalReport()}。</p>
 *
 * <p>兼容性：LLM 调用失败或返回无效 JSON 时，自动回退 {@link ResearchPlanAssembler}
 * （纯 Java 拼接各阶段产物），保证报告字段永不空缺、管线不因报告环节中断。</p>
 */
@Component
public class ReportStage implements PipelineAgent {

    private final ReportGenerationAgent agent;

    public ReportStage(ReportGenerationAgent agent) {
        this.agent = agent;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.REPORT;
    }

    @Override
    public void execute(PipelineContext ctx) {
        try {
            ctx.setFinalReport(agent.generate(ctx));
        } catch (Exception exception) {
            // LLM 失败回退组装器：报告保底产出，不中断管线
            ctx.setFinalReport(ResearchPlanAssembler.assemble(ctx));
        }
    }
}
