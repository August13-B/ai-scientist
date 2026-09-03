package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineModels.QuestionQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ① 问题理解 Agent 测试：正常拆解 / 结构校验 / 无效 JSON（mock BailianClient，不发真实 HTTP）。 */
class ProblemUnderstandingAgentTest {

    private static final String QUESTION = "如何提升水稻病害模型在跨地区小样本场景的泛化能力？";

    private static final String VALID_JSON = """
            {"originalQuestion":"%s","domain":"农业人工智能",
             "subQueries":["跨地区病害图像差异分析","小样本条件下的识别方法","泛化能力评估指标"],
             "keyConcepts":["水稻病害","迁移学习","小样本"],"knownConditions":["标注数据有限"],
             "targetVariables":["跨地区识别准确率"]}
            """.formatted(QUESTION);

    @Test
    void understandsQuestionIntoStructuredQuery() {
        BailianClient bailian = mock(BailianClient.class);
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(VALID_JSON);
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        QuestionQuery query = agent.understand(QUESTION);

        assertEquals(QUESTION, query.originalQuestion());
        assertEquals("农业人工智能", query.domain());
        assertEquals(3, query.subQueries().size());
        assertEquals("水稻病害", query.keyConcepts().get(0));
        assertEquals(List.of("标注数据有限"), query.knownConditions());
        assertEquals(List.of("跨地区识别准确率"), query.targetVariables());
        verify(bailian).chat(anyString(), anyString(), anyString());
    }

    @Test
    void defaultsDomainToGenericScienceWhenMissing() {
        BailianClient bailian = mock(BailianClient.class);
        String jsonWithoutDomain = VALID_JSON.replace("\"domain\":\"农业人工智能\",", "");
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(jsonWithoutDomain);
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        QuestionQuery query = agent.understand(QUESTION);

        assertEquals("通用科研", query.domain());
    }

    @Test
    void acceptsEmptyKnownConditionsAndTargetVariables() {
        BailianClient bailian = mock(BailianClient.class);
        String json = VALID_JSON.replace("\"knownConditions\":[\"标注数据有限\"],", "")
                .replace("\"targetVariables\":[\"跨地区识别准确率\"]", "\"targetVariables\":[]");
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(json);
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        QuestionQuery query = agent.understand(QUESTION);

        assertEquals(List.of(), query.knownConditions());
        assertEquals(List.of(), query.targetVariables());
    }

    @Test
    void rejectsResultWithoutSubQueries() {
        BailianClient bailian = mock(BailianClient.class);
        String json = VALID_JSON.replace("\"subQueries\":[\"跨地区病害图像差异分析\",\"小样本条件下的识别方法\",\"泛化能力评估指标\"],",
                "\"subQueries\":[],");
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(json);
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> agent.understand(QUESTION));

        assertTrue(error.getMessage().contains("子查询"));
    }

    @Test
    void rejectsResultWithBlankKeyConcepts() {
        BailianClient bailian = mock(BailianClient.class);
        String json = VALID_JSON.replace("\"keyConcepts\":[\"水稻病害\",\"迁移学习\",\"小样本\"]",
                "\"keyConcepts\":[\"水稻病害\",\"\",\"小样本\"]");
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(json);
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> agent.understand(QUESTION));

        assertTrue(error.getMessage().contains("关键概念"));
    }

    @Test
    void rejectsMalformedJson() {
        BailianClient bailian = mock(BailianClient.class);
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn("{not-json");
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> agent.understand(QUESTION));
    }

    @Test
    void acceptsJsonWrappedInMarkdownCodeFences() {
        BailianClient bailian = mock(BailianClient.class);
        when(bailian.chat(anyString(), anyString(), anyString()))
                .thenReturn("```json\n" + VALID_JSON + "\n```");
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        QuestionQuery query = agent.understand(QUESTION);

        assertEquals("农业人工智能", query.domain());
    }

    @Test
    void trimsOriginalQuestionToInput() {
        BailianClient bailian = mock(BailianClient.class);
        // 模型可能回显带空格的原文，输出应以输入为准
        String json = VALID_JSON.replace(QUESTION, "  " + QUESTION + "  ");
        when(bailian.chat(anyString(), anyString(), anyString())).thenReturn(json);
        ProblemUnderstandingAgent agent = new ProblemUnderstandingAgent(bailian, new ObjectMapper());

        QuestionQuery query = agent.understand("  " + QUESTION + "  ");

        assertEquals(QUESTION, query.originalQuestion());
        assertTrue(query.subQueries().size() >= 1);
    }
}
