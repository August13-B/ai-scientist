package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.LiteratureRetrievalAgent;
import com.aiscientist.ai.pipeline.PipelineModels.QuestionQuery;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ② 文献检索阶段接入适配器（张睿负责）。
 *
 * <p>实现 {@link PipelineAgent}：从 {@code ctx.getQuestionQuery()} 读 ① 的结构化子查询，
 * 检索论文库/证据库并 LLM 提炼后写入 {@code ctx.setLiterature()}（LiteratureResult）。</p>
 *
 * <p>并行说明：② 与 ③ 知识发现在 {@code LITERATURE/KNOWLEDGE} 并行组执行（互不依赖，
 * ③ 自足 RAG 不读本阶段产物）；本阶段输出供 ④ 假设生成消费 {@code papers}。
 * ① 已先于并行组串行执行，故 {@code getQuestionQuery()} 正常情况下非空；此处仍做
 * null 兜底（① 未接入/直跑时按原始问题单条检索 + 通用科研域），保证阶段可独立运行。</p>
 */
@Component
public class LiteratureRetrievalStage implements PipelineAgent {

    private final LiteratureRetrievalAgent agent;

    public LiteratureRetrievalStage(LiteratureRetrievalAgent agent) {
        this.agent = agent;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.LITERATURE;
    }

    @Override
    public void execute(PipelineContext ctx) {
        QuestionQuery query = ctx.getQuestionQuery();
        if (query == null) {
            // 兜底：① 未接入/直跑时，以原始问题为单条子查询
            query = new QuestionQuery(
                    ctx.getQuestion(),
                    "通用科研",
                    List.of(ctx.getQuestion()),
                    List.of(),
                    List.of(),
                    List.of());
        }
        ctx.setLiterature(agent.retrieve(query));
    }
}
