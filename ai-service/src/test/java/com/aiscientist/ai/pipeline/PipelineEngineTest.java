package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编排引擎单元测试（防回归：队友接入 Agent 时不得破坏调度顺序/并行/异常中断）。
 *
 * <p>测试策略（纯单元测试，不启动 Spring 上下文）：
 * 用 {@link RecordingAgent} 假 Agent 注入 {@link PipelineEngine}，
 * 验证编排顺序、并行调度、阶段缺失跳过与异常传播。</p>
 */
class PipelineEngineTest {

    @Test
    void runsStagesInPipelineOrderWithParallelMiddleGroup() {
        // ①→②③④(并行)→⑤→⑥→⑦：记录执行顺序并断言边界
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> order.add("understanding")),
                agent(AgentStage.LITERATURE, ctx -> order.add("literature")),
                agent(AgentStage.KNOWLEDGE, ctx -> order.add("knowledge")),
                agent(AgentStage.HYPOTHESIS, ctx -> order.add("hypothesis")),
                agent(AgentStage.EVALUATION, ctx -> order.add("evaluation")),
                agent(AgentStage.EXPERIMENT, ctx -> order.add("experiment")),
                agent(AgentStage.DEBATE, ctx -> order.add("debate"))
        ));

        PipelineContext ctx = engine.run("  如何提升水稻病害模型泛化能力？  ");

        // 问题入参 trim 后写入数据总线
        assertEquals("如何提升水稻病害模型泛化能力？", ctx.getQuestion());
        // 10 字段报告组装完成
        assertNotNull(ctx.getFinalReport());
        // 七阶段全部执行
        for (String name : List.of("understanding", "literature", "knowledge",
                "hypothesis", "evaluation", "experiment", "debate")) {
            assertTrue(order.contains(name), "缺少阶段执行记录: " + name);
        }
        // ① 必须最先执行
        assertEquals("understanding", order.get(0));
        // 并行组（②③④）必须在 ⑤ 之前聚合完成
        int evaluation = order.indexOf("evaluation");
        assertTrue(order.indexOf("literature") < evaluation);
        assertTrue(order.indexOf("knowledge") < evaluation);
        assertTrue(order.indexOf("hypothesis") < evaluation);
        // ⑤⑥⑦ 串行顺序
        assertTrue(evaluation < order.indexOf("experiment"));
        assertTrue(order.indexOf("experiment") < order.indexOf("debate"));
    }

    @Test
    void executesParallelStagesConcurrently() {
        // 专项并发验证：若并行组被误改为串行，同一时刻最多只有 1 个 Agent 在执行
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        Consumer<PipelineContext> overlap = ctx -> {
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            try {
                Thread.sleep(300);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                active.decrementAndGet();
            }
        };
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> {
                }),
                agent(AgentStage.LITERATURE, overlap),
                agent(AgentStage.KNOWLEDGE, overlap),
                agent(AgentStage.HYPOTHESIS, overlap),
                agent(AgentStage.EVALUATION, ctx -> {
                }),
                agent(AgentStage.EXPERIMENT, ctx -> {
                }),
                agent(AgentStage.DEBATE, ctx -> {
                })
        ));

        engine.run("研究问题");

        assertTrue(maxActive.get() >= 2,
                "②③④ 未重叠执行（并发峰值=" + maxActive.get() + "），疑似被串行化");
    }

    @Test
    void skipsStagesWithoutAgents() {
        // 当前仓库真实状态：仅知识发现已接入，其余阶段应自动跳过不影响管线
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.KNOWLEDGE, ctx -> ctx.setKnowledgeDiscovery(
                        discoveryResult()))));

        PipelineContext ctx = engine.run("研究问题");

        assertEquals(List.of(AgentStage.KNOWLEDGE), ctx.completedStages());
        assertNotNull(ctx.getFinalReport());
        assertFalse(ctx.getFinalReport().references().isEmpty(),
                "未接入阶段以占位填充，最终报告仍可组装");
    }

    @Test
    void wrapsAgentFailureAndAbortsRemainingStages() {
        // 某 Agent 抛异常 → 包装为 IllegalStateException，后续阶段不再执行
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> {
                    throw new IllegalStateException("模型返回无效");
                }),
                agent(AgentStage.EVALUATION, ctx -> order.add("evaluation"))
        ));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> engine.run("研究问题"));

        assertTrue(error.getMessage().contains("UNDERSTANDING"),
                "异常消息应包含失败阶段");
        assertTrue(error.getMessage().contains("RecordingAgent"),
                "异常消息应包含失败 Agent 类名");
        assertTrue(order.isEmpty(), "阶段失败后后续阶段不应执行");
    }

    @Test
    void rejectsBlankQuestion() {
        PipelineEngine engine = new PipelineEngine(List.of());

        assertThrows(IllegalArgumentException.class, () -> engine.run("  "));
        assertThrows(IllegalArgumentException.class, () -> engine.run(""));
        assertThrows(IllegalArgumentException.class, () -> engine.run(null));
    }

    @Test
    void rejectsNullContextOnResume() {
        PipelineEngine engine = new PipelineEngine(List.of());

        assertThrows(IllegalArgumentException.class, () -> engine.resume(null));
    }

    /** 假 Agent：记录执行顺序或执行自定义动作（动作可访问数据总线 ctx） */
    private static RecordingAgent agent(AgentStage stage, Consumer<PipelineContext> action) {
        return new RecordingAgent(stage, action);
    }

    private static final class RecordingAgent implements PipelineAgent {

        private final AgentStage stage;
        private final Consumer<PipelineContext> action;

        RecordingAgent(AgentStage stage, Consumer<PipelineContext> action) {
            this.stage = stage;
            this.action = action;
        }

        @Override
        public AgentStage stage() {
            return stage;
        }

        @Override
        public void execute(PipelineContext ctx) {
            action.accept(ctx);
        }
    }

    /** 最小知识发现产物（completedStages 依据数据总线产物判断） */
    private static DiscoveryResult discoveryResult() {
        return new DiscoveryResult(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                "如何提升水稻病害模型泛化能力？",
                "跨地区小样本水稻病害识别",
                "研究跨地区小样本条件下的水稻病害识别方法。",
                List.of("doi:10.1000/a"));
    }
}
