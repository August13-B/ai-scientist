package com.aiscientist.ai.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.DiscoveryRequest;
import static com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeDiscoveryModelsTest {

    @Test
    void paperEvidenceRequiresTraceableSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaperEvidence("论文", "摘要", List.of(), 2025,
                        null, null, null));
    }

    @Test
    void paperEvidenceUsesStableSourcePriority() {
        PaperEvidence paper = new PaperEvidence("论文", "摘要", List.of("作者"), 2025,
                "10.1000/test", "123456", "https://example.org/paper");

        assertEquals("doi:10.1000/test", paper.sourceId());
    }

    @Test
    void discoveryRequestRequiresQuestionAndPositiveTopK() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiscoveryRequest(" ", "材料科学", List.of(), 5));
        assertThrows(IllegalArgumentException.class,
                () -> new DiscoveryRequest("研究问题", "材料科学", List.of(), 0));
    }
}
