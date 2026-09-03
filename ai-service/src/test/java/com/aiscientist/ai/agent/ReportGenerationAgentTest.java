package com.aiscientist.ai.agent;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineContext;
import com.aiscientist.ai.pipeline.PipelineModels;
import com.aiscientist.ai.pipeline.ResearchPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** ⑧ 报告生成 Agent 测试：10 字段生成 / references 白名单 / 字段缺失兜底（mock LLM）。 */
class ReportGenerationAgentTest {

    private static final String VALID_REPORT = """
            {"problemStatement":"如何提升水稻病害模型泛化能力？",
             "rationale":"多 Agent 协作得出：以迁移学习为中心，融合 ④ 推理与 ⑦ 辩论共识",
             "technicalDetails":["预训练","微调"],
             "datasets":{"source":["PlantVillage"],"target":["跨地区田间采集"]},
             "paperTitle":"跨地区小样本水稻病害识别",
             "paperAbstract":"研究跨地区小样本水稻病害识别方法。",
             "methods":["迁移学习"],
             "experiments":{"baselines":["CNN"],"metrics":["F1"]},
             "results":"预期跨地区 F1 提升 5%-10%",
             "references":["doi:10.21275/sr231218142714"]}
            """;

    @Test
    void generatesTenFieldReportFromAllAgents() {
        BailianClient bailian = mock(BailianClient.class);
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(VALID_REPORT);
        ReportGenerationAgent agent = new ReportGenerationAgent(bailian, new ObjectMapper(), false);

        ResearchPlan plan = agent.generate(context());

        assertEquals("如何提升水稻病害模型泛化能力？", plan.problemStatement());
        assertEquals(1, plan.references().size());
        assertEquals("doi:10.21275/sr231218142714", plan.references().get(0));
        assertTrue(plan.datasets().source().contains("PlantVillage"));
        assertTrue(plan.experiments().baselines().contains("CNN"));
    }

    @Test
    void filtersReferencesOutsideWhitelist() {
        BailianClient bailian = mock(BailianClient.class);
        // LLM 引用了白名单外的 doi:10.1000/fake，应被过滤为白名单子集
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(VALID_REPORT.replace("doi:10.21275/sr231218142714",
                        "doi:10.1000/fake"));
        ReportGenerationAgent agent = new ReportGenerationAgent(bailian, new ObjectMapper(), false);

        ResearchPlan plan = agent.generate(context());

        assertTrue(plan.references().stream().allMatch(ref -> ref.startsWith("doi:10.21275")));
        assertTrue(!plan.references().contains("doi:10.1000/fake"));
    }

    @Test
    void fallsBackToWhitelistWhenReferencesMissing() {
        BailianClient bailian = mock(BailianClient.class);
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(VALID_REPORT.replace("\"references\":[\"doi:10.21275/sr231218142714\"]",
                        "\"references\":[]"));
        ReportGenerationAgent agent = new ReportGenerationAgent(bailian, new ObjectMapper(), false);

        ResearchPlan plan = agent.generate(context());

        assertTrue(!plan.references().isEmpty(), "references 缺时应回退白名单");
    }

    @Test
    void backfillsMissingFieldsFromStageProducts() {
        BailianClient bailian = mock(BailianClient.class);
        // rationale 为空 → 用 ④ bestHypothesis 的 rationale 兜底
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(VALID_REPORT.replace("\"rationale\":\"多 Agent 协作得出：以迁移学习为中心，融合 ④ 推理与 ⑦ 辩论共识\"",
                        "\"rationale\":\"\""));
        ReportGenerationAgent agent = new ReportGenerationAgent(bailian, new ObjectMapper(), false);

        ResearchPlan plan = agent.generate(context());

        assertTrue(plan.rationale().length() > 5, "rationale 空应用阶段产物兜底");
    }

    @Test
    void rejectsMalformedModelJson() {
        BailianClient bailian = mock(BailianClient.class);
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn("{not-json");
        ReportGenerationAgent agent = new ReportGenerationAgent(bailian, new ObjectMapper(), false);

        assertThrows(IllegalStateException.class, () -> agent.generate(context()));
    }

    @Test
    void productionModeLocksDatasetsFromRealSources() {
        // 生产模式：datasets 不来自 LLM 编造，Source 锁定到⑥实验设计产物、Target 来自⑤核验引用
        BailianClient bailian = mock(BailianClient.class);
        // dto 里 datasets 是编造的 "plantvillage-fake"，生产模式会被忽略
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn(VALID_REPORT.replace("\"source\":[\"PlantVillage\"]",
                        "\"source\":[\"fake-dataset\"]").replace("\"target\":[\"跨地区田间采集\"]",
                        "\"target\":[\"fake-target\"]"));
        ReportGenerationAgent agent = new ReportGenerationAgent(bailian, new ObjectMapper(), false);

        ResearchPlan plan = agent.generate(context());

        // 生产锁定：source 取⑥产物（含 "PlantVillage"），忽略 dto 的 "fake-dataset"；target 来自⑤引用
        assertTrue(plan.datasets().source().contains("PlantVillage"));
        assertTrue(plan.datasets().source().stream().noneMatch(
                item -> item.contains("fake-dataset")), "生产模式不应使用 LLM 编造的数据集名");
    }

    // ==================== 工具：构造含 ①-⑦ 产物的 ctx ====================

    private static PipelineContext context() {
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion("如何提升水稻病害模型泛化能力？");
        ctx.setQuestionQuery(new PipelineModels.QuestionQuery(
                "如何提升水稻病害模型泛化能力？", "农业人工智能",
                List.of("跨地区差异分析", "小样本方法"), List.of("水稻病害", "迁移学习"),
                List.of("标注有限"), List.of("泛化准确率")));
        ctx.setLiterature(new PipelineModels.LiteratureResult(
                List.of(paper()), List.of(), List.of()));
        ctx.setKnowledgeDiscovery(new DiscoveryResult(
                List.of("已知发现"), List.of("局限"), List.of(), List.of("迁移机会"),
                List.of(new KnowledgeDiscoveryModels.ResearchGap("泛化缺口", List.of("doi:10.1000/a"), 0.8, "原因")),
                "如何提升泛化能力？", "跨地区水稻病害识别", "摘要。",
                List.of("doi:10.21275/sr231218142714")));
        ctx.setHypothesis(new PipelineModels.HypothesisResult(List.of(
                new PipelineModels.Hypothesis("候选假设", "以迁移学习为中心提升泛化",
                        List.of("预训练"), List.of("迁移学习"),
                        List.of("推理链一", "推理链二"), List.of("doi:10.21275/sr231218142714")))));
        ctx.setEvaluation(new PipelineModels.EvaluationResult(
                List.of(new PipelineModels.ScoredHypothesis("候选假设", 0.8, 0.9, 0.95, 0.7, 0.85)),
                List.of(), List.of("doi:10.21275/sr231218142714")));
        ctx.setExperiment(new PipelineModels.ExperimentResult(
                List.of("CNN"), List.of("F1"), List.of("PlantVillage"), "预期 F1 提升 5%-10%"));
        ctx.setDebate(new PipelineModels.DebateResult(
                List.of("倡议者：……", "质疑者：……"), "辩论后一致认为可行"));
        return ctx;
    }

    private static PaperEvidence paper() {
        return new PaperEvidence("论文标题", "摘要内容", List.of("作者"), 2023,
                "10.21275/sr231218142714", null, null);
    }
}
