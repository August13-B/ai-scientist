package com.aiscientist.ai.wangwanying.evidence;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record Evidence(
        @NotBlank String taskId,
        @NotBlank String runId,
        @NotBlank String evidenceId,
        @NotNull EvidenceModality modality,
        @NotBlank String subject,
        @NotBlank String predicate,
        @NotBlank String object,
        @NotBlank String statement,
        @Pattern(regexp = "^$|10\\.\\d{4,9}/[-._;()/:A-Za-z0-9]+$", message = "DOI格式不正确") String doi,
        @Pattern(regexp = "^$|\\d{1,9}$", message = "PMID格式不正确") String pmid,
        @NotBlank String sourceTitle,
        @Min(1900) @Max(2100) int year,
        String fileName,
        @Min(1) Integer page,
        String sourceUri,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
        String observation,
        List<String> tags) {

    public Evidence {
        taskId = required(taskId, "taskId");
        runId = required(runId, "runId");
        evidenceId = required(evidenceId, "evidenceId");
        doi = clean(doi);
        pmid = clean(pmid);
        fileName = clean(fileName);
        sourceUri = clean(sourceUri);
        observation = clean(observation);
        tags = tags == null ? List.of() : tags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (doi.isBlank() && pmid.isBlank() && sourceUri.isBlank()) {
            throw new IllegalArgumentException("DOI、PMID和sourceUri至少提供一个");
        }
        if (modality != EvidenceModality.TEXT && observation.isBlank()) {
            throw new IllegalArgumentException("图片、图表、表格或CSV证据必须提供observation");
        }
    }

    public Evidence(
            String subject, String predicate, String object, String statement,
            String doi, String pmid, String sourceTitle, int year, List<String> tags) {
        this("default-task", "default-run",
                doi == null || doi.isBlank() ? "pmid:" + pmid : "doi:" + doi,
                EvidenceModality.TEXT, subject, predicate, object, statement,
                doi, pmid, sourceTitle, year, "", null, "", 1.0, "", tags);
    }

    public String id() {
        return evidenceId;
    }

    public String searchableText() {
        return String.join(" ", subject, predicate, object, statement, sourceTitle,
                observation, fileName, String.join(" ", tags)).toLowerCase();
    }

    private static String required(String value, String field) {
        String cleaned = clean(value);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return cleaned;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}