package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.ProblemUnderstandingAgent;
import org.springframework.stereotype.Component;

/**
 * ① 问题理解阶段接入适配器（张睿负责）。
 *
 * <p>实现 {@link PipelineAgent}：从 {@code ctx.getQuestion()} 读科研问题，
 * 拆解为结构化子查询后写入 {@code ctx.setQuestionQuery()}（QuestionQuery）。</p>
 *
 * <p>② 文献检索 / ③ 知识发现 / ④ 假设生成消费：② 取 {@code subQueries} 逐条检索，
 * 各阶段取 {@code domain} 作为检索域。问题理解是管线首步（UNDERSTANDING 串行最先执行），
 * 其输出必然先于 ②∥③ 并行组就绪，无竞态。</p>
 */
@Component
public class ProblemUnderstandingStage implements PipelineAgent {

    private final ProblemUnderstandingAgent agent;

    public ProblemUnderstandingStage(ProblemUnderstandingAgent agent) {
        this.agent = agent;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.UNDERSTANDING;
    }

    @Override
    public void execute(PipelineContext ctx) {
        ctx.setQuestionQuery(agent.understand(ctx.getQuestion()));
    }
}
