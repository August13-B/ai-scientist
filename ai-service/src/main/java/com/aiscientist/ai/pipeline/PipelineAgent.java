package com.aiscientist.ai.pipeline;

/**
 * 管线 Agent 统一接入接口（可插拔契约）。
 *
 * <p>队友实现自己的 Agent 时：实现本接口，声明所属阶段，
 * 在 {@link #execute(PipelineContext)} 中从 Context 读输入、把结果写入 Context 对应字段。
 * 完成后 Spring 会自动收集（构造注入 List{@code <PipelineAgent>}），无需手工注册。</p>
 *
 * <p>内部实现方式不限：可直接写逻辑，也可包装已有类（如 {@code KnowledgeDiscoveryStage}
 * 包装马艺萌的 KnowledgeDiscoveryAgent）。</p>
 *
 * <p>必读：仓库根目录 AGENTS.md（AI 接入规范）与 docs/agents.md（管线设计）。</p>
 */
public interface PipelineAgent {

    /**
     * 声明本 Agent 属于哪个管线阶段。
     * 框架按 AgentStage 顺序执行；并行阶段（LITERATURE/KNOWLEDGE/HYPOTHESIS）
     * 会被并发调度。
     */
    AgentStage stage();

    /**
     * 执行本 Agent 的核心逻辑。
     *
     * @param ctx 管线数据总线：读 {@code ctx.getXxx()} 输入，写 {@code ctx.setXxx()} 输出
     * @throws Exception 允许抛出，框架会包装为阶段执行失败并中断管线
     */
    void execute(PipelineContext ctx) throws Exception;
}
