package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.LiteratureRetrievalAgent;
import com.aiscientist.ai.pipeline.PipelineModels.QuestionQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** ② 文献检索 Stage 测试：ctx 输入输出映射 / ① 缺失时兜底构造查询。 */
class LiteratureRetrievalStageTest {

    @Test
    void mapsQuestionQueryToAgentAndWritesLiterature() {
        LiteratureRetrievalAgent agent = mock(LiteratureRetrievalAgent.class);
        LiteratureRetrievalStage stage = new LiteratureRetrievalStage(agent);
        QuestionQuery query = new QuestionQuery(
                "如何提升泛化能力？", "农业人工智能",
                List.of("子查询一"), List.of("概念"), List.of(), List.of());
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion("如何提升泛化能力？");
        ctx.setQuestionQuery(query);

        stage.execute(ctx);

        ArgumentCaptor<QuestionQuery> captor = ArgumentCaptor.forClass(QuestionQuery.class);
        verify(agent).retrieve(captor.capture());
        assertEquals(query, captor.getValue());
    }

    @Test
    void fallsBackToOriginalQuestionWhenQuestionQueryMissing() {
        // ① 未接入/直跑：以原始问题为单条子查询 + 通用科研域，阶段可独立运行
        LiteratureRetrievalAgent agent = mock(LiteratureRetrievalAgent.class);
        LiteratureRetrievalStage stage = new LiteratureRetrievalStage(agent);
        PipelineContext ctx = new PipelineContext();
        ctx.setQuestion("如何提升水稻病害模型泛化能力？");

        stage.execute(ctx);

        ArgumentCaptor<QuestionQuery> captor = ArgumentCaptor.forClass(QuestionQuery.class);
        verify(agent).retrieve(captor.capture());
        QuestionQuery fallback = captor.getValue();
        assertEquals("如何提升水稻病害模型泛化能力？", fallback.originalQuestion());
        assertEquals("通用科研", fallback.domain());
        assertEquals(List.of("如何提升水稻病害模型泛化能力？"), fallback.subQueries());
        assertEquals(null, ctx.getQuestionQuery(), "兜底仅用于内部构造，不写回 ctx");
    }
}
