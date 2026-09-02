package com.aiscientist.ai.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 七 Agent 管线编排引擎（DAG 调度）。
 *
 * <p>执行顺序（对应 docs/agents.md 编排图）：</p>
 * <pre>
 * ① 问题理解
 *   → ② 文献检索 / ③ 知识发现 / ④ 假设生成  【并行】
 *   → 【人在回路暂停点：WAITING_HUMAN】
 *   → ⑤ 科学假设评估
 *   → ⑥ 实验设计
 *   → ⑦ 思辨辩论
 *   → 输出 10 字段《科学假设与研究计划》
 * </pre>
 *
 * <p>Agent 可插拔：所有实现 {@link PipelineAgent} 的 Spring Bean 会被自动收集，
 * 按 {@link AgentStage} 分组调度。某阶段暂无人实现则自动跳过，不影响管线运行。</p>
 *
 * <p>人在回路：并行阶段聚合后暂停，发布 {@code pipeline.pause} 事件；
 * 外部调用 {@link #resume(PipelineContext)} 继续。未配置 HumanInLoop 处理器时默认放行。</p>
 *
 * <p>TODO（张睿）：SSE 事件对接前端、State 持久化与断点恢复。</p>
 */
@Component
public class PipelineEngine {

    /** 并行阶段：② ③ ④ */
    private static final List<AgentStage> PARALLEL_STAGES = List.of(
            AgentStage.LITERATURE, AgentStage.KNOWLEDGE, AgentStage.HYPOTHESIS);

    private final Map<AgentStage, List<PipelineAgent>> agentsByStage;
    private final ExecutorService parallelPool = Executors.newFixedThreadPool(
            Math.max(2, PARALLEL_STAGES.size()));

    /**
     * 构造注入：Spring 自动收集所有 PipelineAgent 实现并按阶段分组。
     *
     * @param agents 全部已接入的 Agent（自动注入，无需手工注册）
     */
    public PipelineEngine(List<PipelineAgent> agents) {
        this.agentsByStage = agents.stream()
                .collect(Collectors.groupingBy(PipelineAgent::stage));
    }

    /**
     * 启动管线：输入科研问题，执行完整七阶段，返回携带最终报告的上下文。
     *
     * @param question 用户输入的科研问题
     * @return 管线上下文（含各阶段产物与 finalReport）
     */
    public PipelineContext run(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion(question.trim());

        executeStage(AgentStage.UNDERSTANDING, ctx);
        executeParallel(PARALLEL_STAGES, ctx);
        pauseForHuman(ctx);
        executeStage(AgentStage.EVALUATION, ctx);
        executeStage(AgentStage.EXPERIMENT, ctx);
        executeStage(AgentStage.DEBATE, ctx);

        ctx.setFinalReport(ResearchPlanAssembler.assemble(ctx));
        return ctx;
    }

    /**
     * 人在回路恢复点：人类审阅修改后调用，从暂停点继续。
     * TODO（张睿 + 吴浩瑜前端）：对接前端介入按钮与 State 持久化。
     */
    public void resume(PipelineContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("ctx must not be null");
        }
        // 当前实现为同步执行：run 内暂停点默认放行；
        // 接入前端后，此方法用于解除阻塞并继续后续阶段。
    }

    /** 串行执行单个阶段（可含多个同阶段 Agent） */
    private void executeStage(AgentStage stage, PipelineContext ctx) {
        List<PipelineAgent> agents = agentsByStage.getOrDefault(stage, List.of());
        for (PipelineAgent agent : agents) {
            try {
                agent.execute(ctx);
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "阶段 " + stage + " 执行失败：" + agent.getClass().getSimpleName(),
                        exception);
            }
        }
    }

    /** 并行执行多个阶段（②③④），全部完成后聚合 */
    private void executeParallel(List<AgentStage> stages, PipelineContext ctx) {
        List<CompletableFuture<Void>> futures = stages.stream()
                .map(stage -> CompletableFuture.runAsync(
                        () -> executeStage(stage, ctx), parallelPool))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /** 人在回路暂停点：聚合后暂停等待人类审阅 */
    private void pauseForHuman(PipelineContext ctx) {
        // TODO（张睿）：发布 pipeline.pause 事件给前端，等待 resume；
        // 当前版本默认放行（同步执行），便于先跑通全链路。
    }
}
