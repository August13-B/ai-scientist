package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.ReportGenerationAgent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ⑧ 报告生成 Stage 测试：ctx 生成 / LLM 失败时回退 assembler。 */
class ReportStageTest {

    @Test
    void writesFinalReportFromAgent() {
        ReportGenerationAgent agent = mock(ReportGenerationAgent.class);
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion("研究问题");
        when(agent.generate(ctx)).thenReturn(minimalReport());

        new ReportStage(agent).execute(ctx);

        assertNotNull(ctx.getFinalReport(), "REPORT 阶段应写入 finalReport");
    }

    @Test
    void fallsBackToAssemblerWhenLlmFails() {
        ReportGenerationAgent agent = mock(ReportGenerationAgent.class);
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion("研究问题");
        when(agent.generate(ctx)).thenThrow(new IllegalStateException("模型返回无效 JSON"));

        // 不抛异常，回退 assembler 保底产出
        new ReportStage(agent).execute(ctx);

        assertNotNull(ctx.getFinalReport(), "LLM 失败应回退 assembler 不中断管线");
    }

    private static ResearchPlan minimalReport() {
        return new ResearchPlan(
                "问题", "思路", List.of("技术"), new ResearchPlan.DatasetPlan(List.of(), List.of()),
                "标题", "摘要", List.of("方法"),
                new ResearchPlan.ExperimentPlan(List.of("基线"), List.of("指标")),
                "结果", List.of("doi:10.21275/sr231218142714"));
    }
}
