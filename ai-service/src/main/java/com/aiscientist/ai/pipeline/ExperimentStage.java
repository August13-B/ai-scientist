package com.aiscientist.ai.pipeline;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import com.aiscientist.ai.rag.RagSearchService;
import com.aiscientist.ai.verify.CitationVerifier;
import com.aiscientist.ai.wangwanying.evidence.Evidence;
import com.aiscientist.ai.wangwanying.evidence.EvidenceModality;
import com.aiscientist.ai.wangwanying.experiment.ExperimentPlanGenerator;
import com.aiscientist.ai.wangwanying.experiment.ExperimentRequest;
import com.aiscientist.ai.wangwanying.experiment.GeneratedExperimentContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** ⑥ 实验设计阶段接入适配器（王婉莹负责）。 */
@Component
public class ExperimentStage implements PipelineAgent {

    private final ExperimentPlanGenerator generator;
    private final RagSearchService ragSearchService;
    /** 调试模式（RAG_MOCK_SAMPLES=true）：放宽 dataset URL 校验，允许「数据集选择标准」描述，便于无 RAG/数据时跑通全链路 */
    private final boolean mockSamples;

    @Autowired
    public ExperimentStage(
            ExperimentPlanGenerator generator,
            @Value("${vector.mock-samples:false}") boolean mockSamples,
            RagSearchService ragSearchService) {
        this.generator = generator;
        this.mockSamples = mockSamples;
        this.ragSearchService = ragSearchService;
    }

    /** 供不依赖 Spring 的单元测试使用。 */
    ExperimentStage(ExperimentPlanGenerator generator, boolean mockSamples) {
        this.generator = generator;
        this.mockSamples = mockSamples;
        this.ragSearchService = null;
    }

    @Override
    public AgentStage stage() {
        return AgentStage.EXPERIMENT;
    }

    @Override
    public void execute(PipelineContext ctx) {
        PipelineModels.EvaluationResult evaluation = requireEvaluation(ctx);
        PipelineModels.ScoredHypothesis best = evaluation.rankings().stream()
                .max(Comparator.comparingDouble(PipelineModels.ScoredHypothesis::overall))
                .orElseThrow(() -> new IllegalStateException("Experiment stage requires a ranked hypothesis"));
        List<Evidence> verifiedLiterature = evaluation.references().stream()
                .map(this::toEvidence)
                .toList();
        // 调试模式（临时关 RAG）：允许无核验引用，generator 仍生成实验方案（dataset URL 校验已放宽）
        if (verifiedLiterature.isEmpty() && !mockSamples) {
            throw new IllegalStateException("Experiment stage requires verified DOI, PMID, or URL references");
        }
        List<PaperEvidence> datasetCandidates = retrieveDatasets(best.summary());
        if (datasetCandidates.isEmpty() && !mockSamples && ragSearchService != null) {
            throw new IllegalStateException("Experiment stage requires traceable datasets from the datasets RAG collection");
        }
        List<Evidence> evidence = new ArrayList<>(verifiedLiterature);
        datasetCandidates.stream().map(this::toDatasetEvidence).forEach(evidence::add);

        String domain = ctx.getQuestionQuery() == null ? "general science" : ctx.getQuestionQuery().domain();
        ExperimentRequest request = new ExperimentRequest(
                "pipeline-task", "pipeline-run", "best-hypothesis",
                "Experiment for selected hypothesis", domain, best.summary(), "primary outcome", null);
        GeneratedExperimentContent generated = generator.generate(request, List.copyOf(evidence));
        GeneratedExperimentContent normalized = applyDatasetWhitelist(generated, datasetCandidates);
        validateGeneratedContent(normalized, best.summary());
        ctx.setExperiment(new PipelineModels.ExperimentResult(
                normalized.baselines(), normalized.metrics(), normalized.datasets(),
                String.join("; ", normalized.expectedResults())));
    }

    private List<PaperEvidence> retrieveDatasets(String hypothesis) {
        if (mockSamples || ragSearchService == null) {
            return List.of();
        }
        return ragSearchService.searchCurated("datasets", hypothesis, 3).stream()
                .filter(item -> item.url() != null && !item.url().isBlank())
                .distinct()
                .toList();
    }

    /**
     * 生产模式下由程序写入数据集白名单，不接受模型自造的数据集名称或裸描述。
     * 这样即使模型漏写 URL，最终实验方案仍只使用四库中已经登记的真实数据源。
     */
    private GeneratedExperimentContent applyDatasetWhitelist(
            GeneratedExperimentContent generated, List<PaperEvidence> datasetCandidates) {
        if (generated == null || datasetCandidates.isEmpty()) {
            return generated;
        }
        List<String> traceableDatasets = datasetCandidates.stream()
                .map(item -> item.title() + " (" + item.sourceId() + ")")
                .distinct()
                .toList();
        return new GeneratedExperimentContent(
                generated.baselines(), generated.metrics(), traceableDatasets,
                generated.procedure(), generated.expectedResults(), generated.risks());
    }

    private void validateGeneratedContent(GeneratedExperimentContent generated, String hypothesis) {
        if (generated == null) {
            throw new IllegalStateException("Experiment generator returned no content");
        }
        if (generated.datasets() == null || generated.datasets().isEmpty()) {
            throw new IllegalStateException("Experiment datasets must not be empty");
        }
        for (String dataset : generated.datasets()) {
            if (dataset == null || dataset.isBlank()) {
                throw new IllegalStateException(
                        "Each experiment dataset must include a name: " + dataset);
            }
            // 生产：具体数据集必须可溯源（http/https 的 URL，或 url: 开头的来源标识，含 url:doc-<sha256>）；
            // 调试模式：允许「数据集选择标准」描述（与 BailianExperimentPlanGenerator 提示词第 2 条一致）
            if (!mockSamples && !(dataset.contains("https://") || dataset.contains("http://")
                    || dataset.contains("url:") || dataset.contains("localdoc://"))) {
                throw new IllegalStateException(
                        "Each experiment dataset must include a name and traceable URL: " + dataset);
            }
        }
        if (generated.expectedResults() == null || generated.expectedResults().isEmpty()) {
            throw new IllegalStateException("Expected results must describe a predicted range or decision criterion");
        }
        for (String expected : generated.expectedResults()) {
            if (expected == null || expected.isBlank()
                    || expected.trim().equalsIgnoreCase(hypothesis.trim())) {
                throw new IllegalStateException(
                        "Expected results must be predictions, not a copy of the hypothesis");
            }
        }
    }
    private PipelineModels.EvaluationResult requireEvaluation(PipelineContext ctx) {
        if (ctx.getEvaluation() == null) {
            throw new IllegalStateException("Experiment stage requires evaluation output");
        }
        return ctx.getEvaluation();
    }

    private Evidence toEvidence(String source) {
        String normalized = source == null ? "" : source.trim();
        String doi = valueOrEmpty(CitationVerifier.extractDoi(normalized));
        String pmid = valueOrEmpty(CitationVerifier.extractPmid(normalized));
        String url = valueOrEmpty(CitationVerifier.extractUrl(normalized));
        String arxiv = CitationVerifier.extractArxiv(normalized);
        if (url.isBlank() && arxiv != null) {
            url = "https://arxiv.org/abs/" + arxiv;
        }
        // url:doc-<sha256> 等本地来源标识：extractUrl 正则只认 http/localdoc，不识别；
        // 但它们来自已灌库的真实来源，直接以完整 source_id 作为可溯源 URL，避免误判 Untraceable。
        if (url.isBlank() && normalized.startsWith("url:")) {
            url = normalized;
        }
        if (doi.isBlank() && pmid.isBlank() && url.isBlank()) {
            throw new IllegalStateException("Untraceable experiment reference: " + normalized);
        }
        return new Evidence(
                "pipeline-task", "pipeline-run", normalized, EvidenceModality.TEXT,
                "verified source", "supports", "selected hypothesis", normalized,
                doi, pmid, normalized, 2026, "", null, url, 1.0, "", List.of("pipeline"));
    }

    private Evidence toDatasetEvidence(PaperEvidence dataset) {
        return new Evidence(
                "pipeline-task", "pipeline-run", dataset.sourceId(), EvidenceModality.TEXT,
                dataset.title(), "提供实验数据集", dataset.content(), dataset.content(),
                "", "", dataset.title(), dataset.year() == null ? 2026 : dataset.year(),
                "", null, dataset.url(), 1.0, "", List.of("pipeline", "allowed-dataset"));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
