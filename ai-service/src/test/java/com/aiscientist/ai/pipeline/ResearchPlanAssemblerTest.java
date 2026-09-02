package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.ResearchGap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 10 字段《科学假设与研究计划》组装器单元测试。
 *
 * <p>验证占位填充逻辑与引用红线（references 严禁为空，对应赛题「引用严禁虚构」）。</p>
 *
 * <p>注意：当前组装器仅消费知识发现与实验设计两路输入（假设生成/评估产物尚未映射到
 * 对应字段），测试如实覆盖现状——队友接入对应 Agent 时应同步更新本测试。</p>
 */
class ResearchPlanAssemblerTest {

    private static final String PENDING = "待生成（对应阶段 Agent 接入后填充）";

    @Test
    void fillsPendingPlaceholdersWhenNoStageProducedOutput() {
        ResearchPlan plan = ResearchPlanAssembler.assemble(new PipelineContext());

        assertEquals(PENDING, plan.problemStatement());
        assertEquals(PENDING, plan.rationale());
        assertEquals(List.of(PENDING), plan.technicalDetails());
        assertEquals(List.of(PENDING), plan.methods());
        assertEquals(PENDING, plan.results());
        // references 必须有兜底占位，保证报告可组装
        assertFalse(plan.references().isEmpty());
        assertEquals(List.of(PENDING), plan.datasets().source());
        assertEquals(List.of(PENDING), plan.datasets().target());
        assertEquals(List.of(PENDING), plan.experiments().baselines());
        assertEquals(List.of(PENDING), plan.experiments().metrics());
    }

    @Test
    void fillsFieldsFromKnowledgeDiscoveryOutput() {
        PipelineContext ctx = new PipelineContext();
        ctx.setKnowledgeDiscovery(discoveryResult());

        ResearchPlan plan = ResearchPlanAssembler.assemble(ctx);

        // 字段 1/5/6/10 由知识发现产出
        assertEquals("如何提升水稻病害模型在跨地区小样本场景的泛化能力？",
                plan.problemStatement());
        assertEquals("面向跨地区小样本的水稻病害识别", plan.paperTitle());
        assertEquals("研究跨地区小样本条件下的水稻病害识别方法。",
                plan.paperAbstract());
        assertEquals(List.of("doi:10.1000/a", "doi:10.1000/b"), plan.references());
    }

    @Test
    void fillsExperimentFieldsButKeepsUnconsumedOutputsAsPlaceholders() {
        PipelineContext ctx = new PipelineContext();
        // 评估产物当前未被组装器消费（Assembler 仅消费知识发现与实验设计输入）
        ctx.setEvaluation(new PipelineModels.EvaluationResult(
                List.of(), List.of(), List.of("doi:10.1000/a")));
        ctx.setExperiment(new PipelineModels.ExperimentResult(
                List.of("CNN 基线", "ViT 基线"),
                List.of("准确率", "F1"),
                List.of("水稻病害数据集"),
                "预期 ViT 准确率提升 5 个百分点"));

        ResearchPlan plan = ResearchPlanAssembler.assemble(ctx);

        // 字段 8/9 由实验设计产出
        assertEquals("预期 ViT 准确率提升 5 个百分点", plan.results());
        assertEquals(List.of("CNN 基线", "ViT 基线"), plan.experiments().baselines());
        assertEquals(List.of("准确率", "F1"), plan.experiments().metrics());
        // 无知识发现产物时 references 保持占位（评估的 references 当前未被消费）
        assertEquals(List.of(PENDING), plan.references());
        // 假设生成未接入：字段 2/3/7 仍为占位
        assertEquals(PENDING, plan.rationale());
        assertEquals(List.of(PENDING), plan.technicalDetails());
        assertEquals(List.of(PENDING), plan.methods());
    }

    @Test
    void rejectsResearchPlanWithoutReferences() {
        // 引用红线：10 字段报告的 references 不允许为空（严禁虚构引用的类型层约束）
        assertThrows(IllegalArgumentException.class, () -> new ResearchPlan(
                "问题", "思路", List.of("技术"),
                new ResearchPlan.DatasetPlan(List.of(), List.of()),
                "标题", "摘要", List.of("方法"),
                new ResearchPlan.ExperimentPlan(List.of(), List.of()),
                "结果", List.of()));
    }

    private static DiscoveryResult discoveryResult() {
        ResearchGap gap = new ResearchGap(
                "跨地区小样本识别缺少统一验证",
                List.of("doi:10.1000/a", "doi:10.1000/b"),
                0.87,
                "两篇论文共同支持且可验证");
        return new DiscoveryResult(
                List.of("视觉模型可识别病害"),
                List.of("地域泛化不足"),
                List.of("小样本条件下模型结论不一致"),
                List.of("迁移自监督学习"),
                List.of(gap),
                "如何提升水稻病害模型在跨地区小样本场景的泛化能力？",
                "面向跨地区小样本的水稻病害识别",
                "研究跨地区小样本条件下的水稻病害识别方法。",
                List.of("doi:10.1000/a", "doi:10.1000/b"));
    }
}
