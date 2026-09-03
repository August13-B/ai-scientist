package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;

import java.util.List;

/**
 * 管线数据总线：贯穿七 Agent 全流程。
 *
 * <p>每个 Agent 从 Context 读取自己需要的输入字段，把结果写入对应输出字段。
 * 阶段与字段对应关系见 docs/agents.md 与 AGENTS.md。</p>
 */
public class PipelineContext {

    private String question;

    /** ① 问题理解输出 */
    private PipelineModels.QuestionQuery questionQuery;

    /** ② 文献检索输出 */
    private PipelineModels.LiteratureResult literature;

    /** ③ 知识发现输出（马艺萌已实现，record 复用） */
    private DiscoveryResult knowledgeDiscovery;

    /** ④ 假设生成输出 */
    private PipelineModels.HypothesisResult hypothesis;

    /** ⑤ 科学假设评估输出 */
    private PipelineModels.EvaluationResult evaluation;

    /** ⑥ 实验设计输出 */
    private PipelineModels.ExperimentResult experiment;

    /** ⑦ 思辨辩论输出 */
    private PipelineModels.DebateResult debate;

    /** 人在回路：人类审阅意见（④ 后暂停点 resume 时写入，供 ⑤⑦ 消费） */
    private PipelineModels.HumanFeedback humanFeedback;

    /** 最终 10 字段《科学假设与研究计划》 */
    private ResearchPlan finalReport;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public PipelineModels.QuestionQuery getQuestionQuery() {
        return questionQuery;
    }

    public void setQuestionQuery(PipelineModels.QuestionQuery questionQuery) {
        this.questionQuery = questionQuery;
    }

    public PipelineModels.LiteratureResult getLiterature() {
        return literature;
    }

    public void setLiterature(PipelineModels.LiteratureResult literature) {
        this.literature = literature;
    }

    public DiscoveryResult getKnowledgeDiscovery() {
        return knowledgeDiscovery;
    }

    public void setKnowledgeDiscovery(DiscoveryResult knowledgeDiscovery) {
        this.knowledgeDiscovery = knowledgeDiscovery;
    }

    public PipelineModels.HypothesisResult getHypothesis() {
        return hypothesis;
    }

    public void setHypothesis(PipelineModels.HypothesisResult hypothesis) {
        this.hypothesis = hypothesis;
    }

    public PipelineModels.EvaluationResult getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(PipelineModels.EvaluationResult evaluation) {
        this.evaluation = evaluation;
    }

    public PipelineModels.ExperimentResult getExperiment() {
        return experiment;
    }

    public void setExperiment(PipelineModels.ExperimentResult experiment) {
        this.experiment = experiment;
    }

    public PipelineModels.DebateResult getDebate() {
        return debate;
    }

    public void setDebate(PipelineModels.DebateResult debate) {
        this.debate = debate;
    }

    public ResearchPlan getFinalReport() {
        return finalReport;
    }

    public void setFinalReport(ResearchPlan finalReport) {
        this.finalReport = finalReport;
    }

    public PipelineModels.HumanFeedback getHumanFeedback() {
        return humanFeedback;
    }

    public void setHumanFeedback(PipelineModels.HumanFeedback humanFeedback) {
        this.humanFeedback = humanFeedback;
    }

    /** 已接入的 Agent 阶段（调试用） */
    public List<AgentStage> completedStages() {
        java.util.ArrayList<AgentStage> done = new java.util.ArrayList<>();
        if (questionQuery != null) {
            done.add(AgentStage.UNDERSTANDING);
        }
        if (literature != null) {
            done.add(AgentStage.LITERATURE);
        }
        if (knowledgeDiscovery != null) {
            done.add(AgentStage.KNOWLEDGE);
        }
        if (hypothesis != null) {
            done.add(AgentStage.HYPOTHESIS);
        }
        if (evaluation != null) {
            done.add(AgentStage.EVALUATION);
        }
        if (experiment != null) {
            done.add(AgentStage.EXPERIMENT);
        }
        if (debate != null) {
            done.add(AgentStage.DEBATE);
        }
        if (finalReport != null) {
            done.add(AgentStage.REPORT);
        }
        return List.copyOf(done);
    }
}
