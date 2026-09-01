package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryAgent;
import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryRequest;
import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 【模板示范】③ 知识发现阶段接入适配器。
 *
 * <p>包装队友（马艺萌）已实现的 {@link KnowledgeDiscoveryAgent}，
 * 使其接入管线框架——不改动原实现，只做输入输出映射。</p>
 *
 * <p>队友提交自己的 Agent 时，仿照本类实现 {@link PipelineAgent} 即可：
 * 声明阶段、从 Context 读输入、把结果写入 Context。</p>
 */
@Component
public class KnowledgeDiscoveryStage implements PipelineAgent {

    private final KnowledgeDiscoveryAgent agent;

    public KnowledgeDiscoveryStage(KnowledgeDiscoveryAgent agent) {
        this.agent = agent;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.KNOWLEDGE;
    }

    @Override
    public void execute(PipelineContext ctx) {
        // 输入：科研问题 + ②文献检索产物（未实现时为空，走 RAG 回退）
        List<PaperEvidence> evidence = ctx.getLiterature() == null
                ? List.of()
                : ctx.getLiterature().papers();

        String domain = ctx.getQuestionQuery() == null
                ? null
                : ctx.getQuestionQuery().domain();

        DiscoveryRequest request = new DiscoveryRequest(
                ctx.getQuestion(),
                domain,
                evidence,
                5
        );

        // 输出：写入数据总线对应字段
        ctx.setKnowledgeDiscovery(agent.discover(request));
    }
}
