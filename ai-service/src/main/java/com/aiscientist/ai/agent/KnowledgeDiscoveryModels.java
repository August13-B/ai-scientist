package com.aiscientist.ai.agent;

import java.util.List;
import java.util.Locale;

/** 知识发现 Agent 的输入、阶段结果与最终输出契约。 */
public final class KnowledgeDiscoveryModels {

    private KnowledgeDiscoveryModels() {
    }

    public record DiscoveryRequest(
            String question,
            String domain,
            List<PaperEvidence> evidence,
            int topK
    ) {
        public DiscoveryRequest {
            question = requireText(question, "question");
            domain = hasText(domain) ? domain.trim() : "通用科研";
            evidence = immutable(evidence);
            if (topK <= 0) {
                throw new IllegalArgumentException("topK must be positive");
            }
        }
    }

    public record PaperEvidence(
            String title,
            String content,
            List<String> authors,
            Integer year,
            String doi,
            String pmid,
            String url
    ) {
        public PaperEvidence {
            title = requireText(title, "title");
            content = requireText(content, "content");
            authors = immutable(authors);
            if (!hasText(doi) && !hasText(pmid) && !hasText(url)) {
                throw new IllegalArgumentException("paper evidence requires DOI, PMID, or URL");
            }
        }

        public String sourceId() {
            if (hasText(doi)) {
                String normalized = doi.trim()
                        .replaceFirst("(?i)^doi\\s*:\\s*", "")
                        .replaceFirst("(?i)^https?://(?:dx\\.)?doi\\.org/", "")
                        .toLowerCase(Locale.ROOT);
                return "doi:" + normalized;
            }
            if (hasText(pmid)) {
                String normalized = pmid.trim()
                        .replaceFirst("(?i)^pmid\\s*:\\s*", "");
                return "pmid:" + normalized;
            }
            return "url:" + url.trim();
        }
    }

    public record PaperAnalysis(
            String sourceId,
            String researchQuestion,
            List<String> methods,
            List<String> findings,
            List<String> limitations,
            List<String> futureWork
    ) {
        public PaperAnalysis {
            sourceId = requireText(sourceId, "sourceId");
            researchQuestion = requireText(researchQuestion, "researchQuestion");
            methods = immutable(methods);
            findings = immutable(findings);
            limitations = immutable(limitations);
            futureWork = immutable(futureWork);
        }
    }

    public record EvidenceExtraction(List<PaperAnalysis> papers) {
        public EvidenceExtraction {
            papers = immutable(papers);
        }
    }

    public record CrossPaperAnalysis(
            List<String> knownFindings,
            List<String> limitations,
            List<String> conflicts,
            List<String> transferOpportunities
    ) {
        public CrossPaperAnalysis {
            knownFindings = immutable(knownFindings);
            limitations = immutable(limitations);
            conflicts = immutable(conflicts);
            transferOpportunities = immutable(transferOpportunities);
        }
    }

    public record ResearchGap(
            String gap,
            List<String> evidenceIds,
            double confidence,
            String rankingReason
    ) {
        public ResearchGap {
            gap = requireText(gap, "gap");
            evidenceIds = immutable(evidenceIds);
            rankingReason = requireText(rankingReason, "rankingReason");
            if (confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("confidence must be between 0 and 1");
            }
        }
    }

    public record DiscoveryResult(
            List<String> knownFindings,
            List<String> limitations,
            List<String> conflicts,
            List<String> transferOpportunities,
            List<ResearchGap> researchGaps,
            String selectedProblem,
            String paperTitle,
            String paperAbstract,
            List<String> references
    ) {
        public DiscoveryResult {
            knownFindings = immutable(knownFindings);
            limitations = immutable(limitations);
            conflicts = immutable(conflicts);
            transferOpportunities = immutable(transferOpportunities);
            researchGaps = immutable(researchGaps);
            selectedProblem = requireText(selectedProblem, "selectedProblem");
            paperTitle = requireText(paperTitle, "paperTitle");
            paperAbstract = requireText(paperAbstract, "paperAbstract");
            references = immutable(references);
        }
    }

    private static String requireText(String value, String field) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
