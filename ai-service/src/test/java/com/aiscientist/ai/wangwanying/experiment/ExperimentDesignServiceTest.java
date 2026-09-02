package com.aiscientist.ai.wangwanying.experiment;

import com.aiscientist.ai.wangwanying.evidence.Evidence;
import com.aiscientist.ai.wangwanying.evidence.EvidenceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExperimentDesignServiceTest {

    @Test
    void retrievesEvidenceInvokesGeneratorAndBuildsPlan() {
        EvidenceRepository repository = mock(EvidenceRepository.class);
        ExperimentPlanGenerator generator = mock(ExperimentPlanGenerator.class);
        Evidence evidence = evidence();
        when(repository.search(anyString(), anyInt())).thenReturn(List.of(evidence));
        when(generator.generate(any(), any())).thenReturn(content());
        ExperimentDesignService service = new ExperimentDesignService(repository, generator);

        ExperimentPlan plan = service.design(request());

        verify(repository).search("RAG reduces unsupported answers error rate", 5);
        verify(generator).generate(any(), any());
        assertThat(plan.datasets()).contains("Dataset A (https://example.org/a)");
        assertThat(plan.supportingEvidence()).containsExactly(evidence);
        assertThat(plan.actualResults().executionStatus()).isEqualTo(ExperimentExecutionStatus.NOT_EXECUTED);
    }

    @Test
    void refusesGenerationWhenRepositoryReturnsNoEvidence() {
        EvidenceRepository repository = mock(EvidenceRepository.class);
        ExperimentPlanGenerator generator = mock(ExperimentPlanGenerator.class);
        when(repository.search(anyString(), anyInt())).thenReturn(List.of());
        ExperimentDesignService service = new ExperimentDesignService(repository, generator);

        assertThatThrownBy(() -> service.design(request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires retrieved evidence");
        verifyNoInteractions(generator);
    }

    private ExperimentRequest request() {
        return new ExperimentRequest("task-1", "run-1", "hypothesis-1", "RAG experiment",
                "computer science", "RAG reduces unsupported answers", "error rate", null);
    }

    private Evidence evidence() {
        return new Evidence("RAG", "reduces", "unsupported answers", "Retrieved evidence supports answers",
                "10.1000/test", "", "Traceable paper", 2025, List.of("rag"));
    }

    private GeneratedExperimentContent content() {
        return new GeneratedExperimentContent(
                List.of("baseline-a", "baseline-b", "baseline-c"),
                List.of("accuracy", "confidence interval", "effect size", "stability", "cost"),
                List.of("Dataset A (https://example.org/a)", "Dataset B (https://example.org/b)", "target observations"),
                List.of("prepare", "split", "train", "evaluate", "report"),
                List.of("decrease", "confidence interval", "bounded cost"),
                List.of("leakage", "small sample", "domain shift"));
    }
}