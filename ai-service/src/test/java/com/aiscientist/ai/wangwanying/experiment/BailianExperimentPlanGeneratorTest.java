package com.aiscientist.ai.wangwanying.experiment;

import com.aiscientist.ai.wangwanying.evidence.Evidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BailianExperimentPlanGeneratorTest {
    private static final String VALID_JSON = """
            {
              "baselines":["baseline-a","baseline-b","baseline-c"],
              "metrics":["accuracy","confidence interval","effect size","stability","cost"],
              "datasets":["Dataset A (https://example.org/a)","Dataset B (https://example.org/b)","target observations"],
              "procedure":["prepare","split","train","evaluate","report"],
              "expectedResults":["error rate decreases by 5%-10%","confidence interval excludes zero","cost remains bounded"],
              "risks":["data leakage","small sample","domain shift"]
            }
            """;

    @Test
    void parsesStructuredPlanFromMockedChatModel() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn(VALID_JSON);
        BailianExperimentPlanGenerator generator = generator(model);

        GeneratedExperimentContent result = generator.generate(request(), List.of(evidence()));

        assertThat(result.baselines()).hasSize(3);
        assertThat(result.datasets()).contains("Dataset A (https://example.org/a)");
        assertThat(result.expectedResults()).contains("error rate decreases by 5%-10%");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(model).chat(prompt.capture());
        assertThat(prompt.getValue()).contains("10.1000/test");
    }

    @Test
    void retriesOnceWhenModelReturnsMalformedJson() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenReturn("not-json", VALID_JSON);

        GeneratedExperimentContent result = generator(model).generate(request(), List.of(evidence()));

        assertThat(result.metrics()).hasSize(5);
        verify(model, times(2)).chat(anyString());
    }

    @Test
    void reportsChatModelFailureWithoutFabricatingAPlan() {
        ChatModel model = mock(ChatModel.class);
        when(model.chat(anyString())).thenThrow(new RuntimeException("upstream unavailable"));

        assertThatThrownBy(() -> generator(model).generate(request(), List.of(evidence())))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    private BailianExperimentPlanGenerator generator(ChatModel model) {
        return new BailianExperimentPlanGenerator(model, new ObjectMapper());
    }

    private ExperimentRequest request() {
        return new ExperimentRequest("task-1", "run-1", "hypothesis-1", "RAG experiment",
                "computer science", "RAG reduces unsupported answers", "error rate", null);
    }

    private Evidence evidence() {
        return new Evidence("RAG", "reduces", "unsupported answers", "Retrieved evidence supports answers",
                "10.1000/test", "", "Traceable paper", 2025, List.of("rag"));
    }
}