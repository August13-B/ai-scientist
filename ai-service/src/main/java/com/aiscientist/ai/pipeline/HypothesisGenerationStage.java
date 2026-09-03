package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.HypothesisGenerationAgent;
import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import org.springframework.stereotype.Component;

import java.util.List;

/** 将真实假设生成 Agent 接入统一七 Agent 管线。 */
@Component
public class HypothesisGenerationStage implements PipelineAgent {
    private final HypothesisGenerationAgent agent;

    public HypothesisGenerationStage(HypothesisGenerationAgent agent) {
        this.agent = agent;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.HYPOTHESIS;
    }

    @Override
    public void execute(PipelineContext ctx) {
        if (ctx.getKnowledgeDiscovery() == null) {
            throw new IllegalStateException("Hypothesis stage requires knowledge discovery output");
        }
        String domain = ctx.getQuestionQuery() == null
                ? "通用科研" : ctx.getQuestionQuery().domain();
        List<PaperEvidence> papers = ctx.getLiterature() == null
                ? List.of() : ctx.getLiterature().papers();
        ctx.setHypothesis(agent.generate(
                ctx.getQuestion(), domain, ctx.getKnowledgeDiscovery(), papers));
    }
}
