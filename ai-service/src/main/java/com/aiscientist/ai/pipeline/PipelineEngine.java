package com.aiscientist.ai.pipeline;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

/**
 * 七 Agent 管线编排引擎（DAG 调度）。
 *
 * <p>执行顺序（方案 B，对应 docs/agents.md 编排图）：</p>
 * <pre>
 * ① 问题理解
 *   → ② 文献检索 / ③ 知识发现  【并行，互不依赖、各自自足 RAG】
 *   → ④ 假设生成（串行，消费 ③ 的 Research Gap / 选题）
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
 * <p>人在回路（异步模式）：{@link #start(String)} 立即返回 runId 并在后台执行管线；
 * 并行组与 ④ 聚合后发布 {@code pipeline.pause} 事件并阻塞等待，外部调用
 * {@link #resume(String, PipelineModels.HumanFeedback)} 释放。同步模式
 * {@link #run(String)}（供测试/内部调用）不注册暂停处理器，自动放行。</p>
 *
 * <p>事件：各阶段前后发布 {@code agent.start} / {@code agent.result}，
 * 流程节点发布 {@code pipeline.pause/resume/done/error}（见 {@link EventPublisher}）。</p>
 */
@Component
public class PipelineEngine {

    /** 并行阶段：② 文献检索 / ③ 知识发现（互不依赖） */
    private static final List<AgentStage> PARALLEL_STAGES = List.of(
            AgentStage.LITERATURE, AgentStage.KNOWLEDGE);

    private final Map<AgentStage, List<PipelineAgent>> agentsByStage;
    private final EventPublisher eventPublisher;
    private final ExecutorService parallelPool = newDaemonPool(
            Math.max(2, PARALLEL_STAGES.size()), "pipeline-parallel");
    /**
     * 每条异步管线使用独立线程。
     *
     * <p>管线会在人工审阅点阻塞等待 resume，固定单线程会导致首个待审任务
     * 占住执行器，使后续任务始终停留在空 trace。缓存线程池让不同任务互不阻塞；
     * 单条任务内部的文献与知识发现仍由 parallelPool 控制并行度。</p>
     */
    private final ExecutorService pipelinePool = newDaemonCachedPool("pipeline-run");
    private final Map<String, PipelineRuntime> runtimes = new ConcurrentHashMap<>();

    /**
     * 测试/无事件场景构造：事件发布为空实现。
     *
     * @param agents 全部已接入的 Agent（自动注入，无需手工注册）
     */
    public PipelineEngine(List<PipelineAgent> agents) {
        this(agents, new NoopEventPublisher());
    }

    /**
     * 生产构造：Spring 自动收集所有 PipelineAgent 并按阶段分组。
     *
     * @param agents         全部已接入的 Agent
     * @param eventPublisher SSE 事件发布器
     */
    @Autowired
    public PipelineEngine(List<PipelineAgent> agents, EventPublisher eventPublisher) {
        this.agentsByStage = agents.stream()
                .collect(Collectors.groupingBy(PipelineAgent::stage));
        this.eventPublisher = eventPublisher;
    }

    /**
     * 同步执行完整管线（测试/内部调用）：不注册人在回路暂停，自动放行。
     *
     * @param question 用户输入的科研问题
     * @return 携带最终报告的上下文
     */
    public PipelineContext run(String question) {
        String trimmed = requireQuestion(question);
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion(trimmed);
        executePipeline(new PipelineRuntime(null, ctx, false));
        return ctx;
    }

    /**
     * 异步启动管线：立即返回 runId，后台执行；在人在回路暂停点等待
     * {@link #resume(String, PipelineModels.HumanFeedback)}。
     *
     * @param question 用户输入的科研问题
     * @return runId（用于 stream / resume / state）
     */
    public String start(String question) {
        String trimmed = requireQuestion(question);
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion(trimmed);
        String runId = UUID.randomUUID().toString();
        PipelineRuntime runtime = new PipelineRuntime(runId, ctx, true);
        runtimes.put(runId, runtime);
        runtime.future = CompletableFuture.runAsync(
                () -> executePipeline(runtime), pipelinePool);
        return runId;
    }

    /**
     * 人在回路恢复：提交人类审阅意见/修改后的候选假设，释放暂停点。
     *
     * @param runId    管线标识
     * @param feedback 人类审阅意见（可为空确认）
     */
    public void resume(String runId, PipelineModels.HumanFeedback feedback) {
        PipelineRuntime runtime = requireRuntime(runId);
        runtime.ctx.setHumanFeedback(feedback);
        eventPublisher.publish(runId, "pipeline.resume",
                Map.of("runId", runId, "comment", feedback == null ? null : feedback.reviewComment()));
        runtime.humanLatch.countDown();
    }

    /** 查询 runId 当前管线状态（各阶段产物，未完成时为 null 字段） */
    public PipelineContext state(String runId) {
        return requireRuntime(runId).ctx;
    }

    /** 获取 runId 的 Agent 级执行追踪（input/output/耗时/状态） */
    public List<AgentTraceRecord> trace(String runId) {
        return List.copyOf(requireRuntime(runId).trace);
    }

    /** 运行信息（调试列表用） */
    public record RunInfo(String runId, String question, boolean done) {
    }

    /** 全部已启动的 run（进行中/已暂停/已完成），按启动顺序 */
    public List<RunInfo> runs() {
        return runtimes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new RunInfo(
                        entry.getKey(),
                        entry.getValue().ctx.getQuestion(),
                        entry.getValue().ctx.getFinalReport() != null))
                .toList();
    }

    /** 获取 runId 的完成信号（供调用方等待管线结束） */
    public CompletableFuture<Void> completion(String runId) {
        return requireRuntime(runId).future;
    }

    /** 注册 runId 的 SSE 订阅（GET /pipeline/{runId}/stream） */
    public void registerStream(String runId, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        if (eventPublisher instanceof SseEventPublisher ssePublisher) {
            ssePublisher.register(runId, emitter);
        }
    }

    // ==================== 编排核心 ====================

    private void executePipeline(PipelineRuntime runtime) {
        PipelineContext ctx = runtime.ctx;
        // 同步模式（run()）runId 为空，事件负载统一用 "sync" 兜底（Map.of 不允许 null）
        String runId = runtime.runId == null ? "sync" : runtime.runId;
        try {
            executeStage(runtime, AgentStage.UNDERSTANDING, runId);
            executeParallel(runtime, PARALLEL_STAGES, runId);
            executeStage(runtime, AgentStage.HYPOTHESIS, runId);
            pauseForHuman(runtime, runId);
            executeStage(runtime, AgentStage.EVALUATION, runId);
            executeStage(runtime, AgentStage.EXPERIMENT, runId);
            executeStage(runtime, AgentStage.DEBATE, runId);
            // ⑧ 报告生成：ReportStage 内已回退 assembler，保底产出
            executeStage(runtime, AgentStage.REPORT, runId);
            if (ctx.getFinalReport() == null) {
                ctx.setFinalReport(ResearchPlanAssembler.assemble(ctx));
            }
            eventPublisher.publish(runId, "pipeline.done",
                    Map.of("runId", runId, "report", ctx.getFinalReport()));
        } catch (Exception exception) {
            eventPublisher.publish(runId, "pipeline.error",
                    Map.of("runId", runId, "message", String.valueOf(exception.getMessage())));
            throw exception;
        } finally {
            eventPublisher.complete(runId);
        }
    }

    /** 人在回路暂停点：异步模式（awaitHuman=true）阻塞等待人类 resume */
    private void pauseForHuman(PipelineRuntime runtime, String runId) {
        if (!runtime.awaitHuman) {
            return;
        }
        eventPublisher.publish(runId, "pipeline.pause",
                Map.of("runId", runId,
                        "message", "等待人类审阅候选假设（POST /pipeline/{runId}/resume）"));
        try {
            runtime.humanLatch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("管线等待人类介入时被中断", interrupted);
        }
    }

    /** 串行执行单个阶段（可含多个同阶段 Agent），逐 Agent 发布事件并记录 trace */
    private void executeStage(PipelineRuntime runtime, AgentStage stage, String runId) {
        List<PipelineAgent> agents = agentsByStage.getOrDefault(stage, List.of());
        PipelineContext ctx = runtime.ctx;
        for (PipelineAgent agent : agents) {
            // 执行前按阶段契约取输入快照（agent 只读输入、写自己输出字段，前后一致）
            Map<String, Object> input = stageInputs(ctx, stage);
            // agent.start 事件携带 input，供前端 SSE 实时展示键入输入
            java.util.Map<String, Object> startEvent = new java.util.LinkedHashMap<>();
            startEvent.put("stage", stage.name());
            startEvent.put("agent", agent.getClass().getSimpleName());
            startEvent.put("input", input);
            eventPublisher.publish(runId, "agent.start", startEvent);
            long startMillis = System.currentTimeMillis();
            try {
                agent.execute(ctx);
                runtime.trace.add(new AgentTraceRecord(
                        stage.name(),
                        agent.getClass().getSimpleName(),
                        startMillis,
                        System.currentTimeMillis() - startMillis,
                        "SUCCESS",
                        null,
                        input,
                        stageOutput(ctx, stage)));
            } catch (Exception exception) {
                runtime.trace.add(new AgentTraceRecord(
                        stage.name(),
                        agent.getClass().getSimpleName(),
                        startMillis,
                        System.currentTimeMillis() - startMillis,
                        "FAILED",
                        String.valueOf(exception.getMessage()),
                        input,
                        null));
                throw new IllegalStateException(
                        "阶段 " + stage + " 执行失败：" + agent.getClass().getSimpleName(),
                        exception);
            }
            // 阶段产物可能为 null（Agent 未产出），Map.of 不允许 null，故用 LinkedHashMap
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("stage", stage.name());
            result.put("agent", agent.getClass().getSimpleName());
            result.put("output", stageOutput(ctx, stage));
            eventPublisher.publish(runId, "agent.result", result);
        }
    }

    /** 并行执行多个阶段（②③），全部完成后聚合 */
    private void executeParallel(PipelineRuntime runtime, List<AgentStage> stages, String runId) {
        List<CompletableFuture<Void>> futures = stages.stream()
                .map(stage -> CompletableFuture.runAsync(
                        () -> executeStage(runtime, stage, runId), parallelPool))
                .toList();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    /** 阶段产物摘要（agent.result 事件负载，未产出为 null） */
    private Object stageOutput(PipelineContext ctx, AgentStage stage) {
        return switch (stage) {
            case UNDERSTANDING -> ctx.getQuestionQuery();
            case LITERATURE -> ctx.getLiterature();
            case KNOWLEDGE -> ctx.getKnowledgeDiscovery();
            case HYPOTHESIS -> ctx.getHypothesis();
            case EVALUATION -> ctx.getEvaluation();
            case EXPERIMENT -> ctx.getExperiment();
            case DEBATE -> ctx.getDebate();
            case REPORT -> ctx.getFinalReport();
        };
    }

    /**
     * 按 AgentStage 数据契约取输入字段快照（对应 docs/agents.md 与 AGENTS.md 契约表）：
     * ① question；② questionQuery+question；③ question+questionQuery（自足 RAG）；
     * ④ knowledgeDiscovery+literature；⑤ hypothesis+humanFeedback；⑥ evaluation；⑦ evaluation+experiment。
     */
    private Map<String, Object> stageInputs(PipelineContext ctx, AgentStage stage) {
        Map<String, Object> input = new LinkedHashMap<>();
        switch (stage) {
            case UNDERSTANDING -> putIfPresent(input, "question", ctx.getQuestion());
            case LITERATURE -> {
                putIfPresent(input, "question", ctx.getQuestion());
                putIfPresent(input, "questionQuery", ctx.getQuestionQuery());
            }
            case KNOWLEDGE -> {
                putIfPresent(input, "question", ctx.getQuestion());
                putIfPresent(input, "questionQuery", ctx.getQuestionQuery());
            }
            case HYPOTHESIS -> {
                putIfPresent(input, "knowledgeDiscovery", ctx.getKnowledgeDiscovery());
                putIfPresent(input, "literature", ctx.getLiterature());
            }
            case EVALUATION -> {
                putIfPresent(input, "hypothesis", ctx.getHypothesis());
                putIfPresent(input, "humanFeedback", ctx.getHumanFeedback());
            }
            case EXPERIMENT -> putIfPresent(input, "evaluation", ctx.getEvaluation());
            case DEBATE -> {
                putIfPresent(input, "evaluation", ctx.getEvaluation());
                putIfPresent(input, "experiment", ctx.getExperiment());
            }
            case REPORT -> {
                putIfPresent(input, "question", ctx.getQuestion());
                putIfPresent(input, "questionQuery", ctx.getQuestionQuery());
                putIfPresent(input, "literature", ctx.getLiterature());
                putIfPresent(input, "knowledgeDiscovery", ctx.getKnowledgeDiscovery());
                putIfPresent(input, "hypothesis", ctx.getHypothesis());
                putIfPresent(input, "evaluation", ctx.getEvaluation());
                putIfPresent(input, "experiment", ctx.getExperiment());
                putIfPresent(input, "debate", ctx.getDebate());
            }
        }
        return input;
    }

    private void putIfPresent(Map<String, Object> input, String key, Object value) {
        if (value != null) {
            input.put(key, value);
        }
    }

    private PipelineRuntime requireRuntime(String runId) {
        PipelineRuntime runtime = runtimes.get(runId);
        if (runtime == null) {
            throw new IllegalArgumentException("未找到管线运行态 runId=" + runId);
        }
        return runtime;
    }

    private static String requireQuestion(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        return question.trim();
    }

    private static ExecutorService newDaemonPool(int size, String name) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, name + "-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(size, factory);
    }

    private static ExecutorService newDaemonCachedPool(String name) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, name + "-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newCachedThreadPool(factory);
    }

    /** 单任务运行态：ctx + 暂停闩 + 完成信号 + 执行追踪 */
    private static final class PipelineRuntime {

        private final String runId;
        private final PipelineContext ctx;
        private final boolean awaitHuman;
        private final CountDownLatch humanLatch = new CountDownLatch(1);
        private final List<AgentTraceRecord> trace = new CopyOnWriteArrayList<>();
        private volatile CompletableFuture<Void> future;

        private PipelineRuntime(String runId, PipelineContext ctx, boolean awaitHuman) {
            this.runId = runId;
            this.ctx = ctx;
            this.awaitHuman = awaitHuman;
        }
    }

    /** 空事件发布器（同步模式/测试） */
    private static final class NoopEventPublisher implements EventPublisher {

        @Override
        public void publish(String taskId, String eventType, Object data) {
            // 无订阅方，事件丢弃
        }
    }
}
