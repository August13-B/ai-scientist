package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 编排引擎单元测试（防回归：队友接入 Agent 时不得破坏调度顺序/并行/异常中断/人在回路）。
 *
 * <p>测试策略（纯单元测试，不启动 Spring 上下文）：
 * 用 {@link RecordingAgent} 假 Agent 注入 {@link PipelineEngine}，
 * 验证编排顺序（方案 B：②∥③ → ④ 串行）、并发调度、阶段缺失跳过、异常传播与人在回路。</p>
 */
class PipelineEngineTest {

    @Test
    void runsStagesInPipelineOrderWithParallelTwoThenSerialHypothesis() {
        // ①→(②∥③)→④→⑤→⑥→⑦：记录执行顺序并断言边界（方案 B）
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> order.add("understanding")),
                agent(AgentStage.LITERATURE, ctx -> order.add("literature")),
                agent(AgentStage.KNOWLEDGE, ctx -> order.add("knowledge")),
                agent(AgentStage.HYPOTHESIS, ctx -> order.add("hypothesis")),
                agent(AgentStage.EVALUATION, ctx -> order.add("evaluation")),
                agent(AgentStage.EXPERIMENT, ctx -> order.add("experiment")),
                agent(AgentStage.DEBATE, ctx -> order.add("debate")),
                agent(AgentStage.REPORT, ctx -> order.add("report"))
        ));

        PipelineContext ctx = engine.run("  如何提升水稻病害模型泛化能力？  ");

        // 问题入参 trim 后写入数据总线
        assertEquals("如何提升水稻病害模型泛化能力？", ctx.getQuestion());
        // 10 字段报告组装完成
        assertNotNull(ctx.getFinalReport());
        // 七阶段全部执行
        for (String name : List.of("understanding", "literature", "knowledge",
                "hypothesis", "evaluation", "experiment", "debate", "report")) {
            assertTrue(order.contains(name), "缺少阶段执行记录: " + name);
        }
        // ① 必须最先执行
        assertEquals("understanding", order.get(0));
        // 并行组（②③）必须在 ④ 之前聚合完成（④ 串行消费 ③ 的 Gap）
        int hypothesis = order.indexOf("hypothesis");
        assertTrue(order.indexOf("literature") < hypothesis, "② 应先于 ④");
        assertTrue(order.indexOf("knowledge") < hypothesis, "③ 应先于 ④");
        // ④ 在 ⑤ 之前，⑤⑥⑦ 串行顺序
        int evaluation = order.indexOf("evaluation");
        assertTrue(hypothesis < evaluation, "④ 应先于 ⑤");
        assertTrue(evaluation < order.indexOf("experiment"));
        assertTrue(order.indexOf("experiment") < order.indexOf("debate"));
        // ⑧ REPORT 在 ⑦ 之后
        assertTrue(order.indexOf("debate") < order.indexOf("report"));
    }

    @Test
    void executesParallelStagesConcurrently() {
        // 专项并发验证：②③ 若被误改为串行，同一时刻最多只有 1 个 Agent 在执行
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
                agent(AgentStage.HYPOTHESIS, ctx -> {
                }),
                agent(AgentStage.EVALUATION, ctx -> {
                }),
                agent(AgentStage.EXPERIMENT, ctx -> {
                }),
                agent(AgentStage.DEBATE, ctx -> {
                })
        ));

        engine.run("研究问题");

        assertTrue(maxActive.get() >= 2,
                "②③ 未重叠执行（并发峰值=" + maxActive.get() + "），疑似被串行化");
    }

    @Test
    void skipsStagesWithoutAgents() {
        // 当前仓库真实状态：仅知识发现已接入，其余阶段应自动跳过不影响管线
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.KNOWLEDGE, ctx -> ctx.setKnowledgeDiscovery(
                        discoveryResult()))));

        PipelineContext ctx = engine.run("研究问题");

        assertEquals(List.of(AgentStage.KNOWLEDGE, AgentStage.REPORT), ctx.completedStages());
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
    void rejectsUnknownRunIdOnResumeStateAndCompletion() {
        PipelineEngine engine = new PipelineEngine(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> engine.resume("unknown-run",
                        new PipelineModels.HumanFeedback("意见", List.of())));
        assertThrows(IllegalArgumentException.class, () -> engine.state("unknown-run"));
        assertThrows(IllegalArgumentException.class, () -> engine.completion("unknown-run"));
    }

    @Test
    void asynchronousStartPausesAtHumanLoopAndResumesToDone() throws Exception {
        // 人在回路：start() 后台执行 → ④ 后发 pipeline.pause 阻塞 → resume 释放 → done
        TestEventPublisher publisher = new TestEventPublisher();
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> {
                }),
                agent(AgentStage.KNOWLEDGE, ctx -> ctx.setKnowledgeDiscovery(discoveryResult())),
                agent(AgentStage.HYPOTHESIS, ctx -> ctx.setHypothesis(hypothesisResult())),
                agent(AgentStage.EVALUATION, ctx -> {
                })
        ), publisher);

        String runId = engine.start("研究问题");

        // 等待暂停点：④ 已产出、pipeline.pause 已发布、⑤ 未执行
        awaitEvent(publisher, "pipeline.pause", 5);
        assertNotNull(engine.state(runId).getHypothesis(), "暂停点应位于 ④ 之后");
        assertTrue(publisher.events().stream().noneMatch(
                event -> event.eventType().equals("pipeline.done")));

        // 人类提交审阅意见后恢复
        engine.resume(runId, new PipelineModels.HumanFeedback("否决假设一，保留假设二", List.of()));

        // 管线继续至完成
        engine.completion(runId).get(5, TimeUnit.SECONDS);
        assertTrue(publisher.events().stream().anyMatch(
                event -> event.eventType().equals("pipeline.done")));
        assertNotNull(engine.state(runId).getFinalReport());
        assertEquals("否决假设一，保留假设二",
                engine.state(runId).getHumanFeedback().reviewComment());
        assertTrue(publisher.events().stream().anyMatch(
                event -> event.eventType().equals("pipeline.resume")));
    }

    @Test
    void synchronousRunSkipsHumanPause() {
        // 同步 run()（测试/内部调用）不注册暂停处理器，自动放行
        TestEventPublisher publisher = new TestEventPublisher();
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.KNOWLEDGE, ctx -> ctx.setKnowledgeDiscovery(discoveryResult()))
        ), publisher);

        PipelineContext ctx = engine.run("研究问题");

        assertFalse(publisher.events().stream().anyMatch(
                event -> event.eventType().equals("pipeline.pause")),
                "同步模式不应触发人在回路暂停");
        assertNotNull(ctx.getFinalReport());
    }

    @Test
    void failurePublishesPipelineErrorEvent() throws Exception {
        // 异步模式：阶段失败发布 pipeline.error，且可被 completion 观察到
        TestEventPublisher publisher = new TestEventPublisher();
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> {
                    throw new IllegalStateException("模型返回无效");
                })
        ), publisher);

        String runId = engine.start("研究问题");

        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> engine.completion(runId).get(5, TimeUnit.SECONDS));
        assertTrue(publisher.events().stream().anyMatch(
                event -> event.eventType().equals("pipeline.error")));
    }

    @Test
    void asynchronousTraceContainsInputOutputAndFailure() throws Exception {
        // 异步 trace：成功 Agent 记 SUCCESS+input/output；失败 Agent 记 FAILED+errorMessage
        TestEventPublisher publisher = new TestEventPublisher();
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> ctx.setQuestionQuery(
                        new PipelineModels.QuestionQuery(
                                ctx.getQuestion(), "农业人工智能",
                                List.of("子查询一"), List.of("概念"), List.of(), List.of()))),
                agent(AgentStage.KNOWLEDGE, ctx -> {
                    if (ctx.getQuestionQuery() == null) {
                        throw new IllegalStateException("缺少问题理解输出");
                    }
                    ctx.setKnowledgeDiscovery(discoveryResult());
                })
        ), publisher);

        String runId = engine.start("研究问题");
        // ④ 后人在回路暂停：resume 释放后才继续到 done
        awaitEvent(publisher, "pipeline.pause", 5);
        engine.resume(runId, new PipelineModels.HumanFeedback("通过", List.of()));
        engine.completion(runId).get(5, TimeUnit.SECONDS);

        List<AgentTraceRecord> trace = engine.trace(runId);
        assertEquals(2, trace.size());
        AgentTraceRecord understanding = trace.get(0);
        assertEquals("SUCCESS", understanding.status());
        assertEquals("UNDERSTANDING", understanding.stage());
        assertTrue(understanding.input().containsKey("question"));
        assertEquals("研究问题", understanding.input().get("question"));
        assertNotNull(understanding.output());
        assertTrue(understanding.durationMillis() >= 0);

        AgentTraceRecord knowledge = trace.get(1);
        assertEquals("SUCCESS", knowledge.status());
        assertEquals("KNOWLEDGE", knowledge.stage());
        assertTrue(knowledge.input().containsKey("questionQuery"));
        assertNotNull(knowledge.output());
    }

    @Test
    void traceRecordsFailedAgentWithErrorMessage() throws Exception {
        // 失败 Agent：trace 记 FAILED + errorMessage，且 input 快照仍在
        TestEventPublisher publisher = new TestEventPublisher();
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.UNDERSTANDING, ctx -> {
                    throw new IllegalStateException("模型返回无效 JSON");
                })
        ), publisher);

        assertThrows(IllegalStateException.class, () -> engine.run("研究问题"));
        // 同步 run 的 runtime 未注册；异步 start 场景验证 trace
        String runId = engine.start("研究问题");
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> engine.completion(runId).get(5, TimeUnit.SECONDS));
        List<AgentTraceRecord> trace = engine.trace(runId);
        assertEquals(1, trace.size());
        assertEquals("FAILED", trace.get(0).status());
        assertTrue(trace.get(0).errorMessage().contains("模型返回无效 JSON"));
        assertTrue(trace.get(0).input().containsKey("question"));
        assertEquals(null, trace.get(0).output());
    }

    @Test
    void listsStartedRuns() throws Exception {
        // runs()：列出已启动 run 与完成状态
        TestEventPublisher publisher = new TestEventPublisher();
        PipelineEngine engine = new PipelineEngine(List.of(
                agent(AgentStage.KNOWLEDGE, ctx -> ctx.setKnowledgeDiscovery(discoveryResult()))
        ), publisher);

        String runId = engine.start("研究问题");
        awaitEvent(publisher, "pipeline.pause", 5);
        engine.resume(runId, new PipelineModels.HumanFeedback("通过", List.of()));
        engine.completion(runId).get(5, TimeUnit.SECONDS);

        List<PipelineEngine.RunInfo> runs = engine.runs();
        assertEquals(1, runs.size());
        assertEquals(runId, runs.get(0).runId());
        assertEquals("研究问题", runs.get(0).question());
        assertTrue(runs.get(0).done());
        assertThrows(IllegalArgumentException.class, () -> engine.trace("unknown"));
    }

    // ==================== 测试工具 ====================

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

    /** 事件发布器测试替身：记录全部事件 */
    private static final class TestEventPublisher implements EventPublisher {

        private final List<TestEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(String taskId, String eventType, Object data) {
            events.add(new TestEvent(eventType, String.valueOf(data)));
        }

        List<TestEvent> events() {
            return events;
        }
    }

    private record TestEvent(String eventType, String data) {
    }

    /** 轮询等待指定事件出现（10ms 间隔，超时抛断言失败） */
    private static void awaitEvent(TestEventPublisher publisher, String eventType, int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (publisher.events().stream().anyMatch(event -> event.eventType().equals(eventType))) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fail("等待事件被打断: " + eventType);
            }
        }
        fail("超时未等到事件: " + eventType);
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

    /** 最小假设生成产物（人在回路暂停点断言用） */
    private static PipelineModels.HypothesisResult hypothesisResult() {
        return new PipelineModels.HypothesisResult(List.of(
                new PipelineModels.Hypothesis(
                        "候选假设一",
                        "基于迁移学习提升泛化",
                        List.of("预训练", "微调"),
                        List.of("迁移学习"),
                        List.of("证据A", "证据B"),
                        List.of("doi:10.1000/a"))));
    }
}
