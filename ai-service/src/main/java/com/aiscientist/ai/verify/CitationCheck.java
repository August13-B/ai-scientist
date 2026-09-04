package com.aiscientist.ai.verify;

/**
 * 引用核验结果（逐条）。
 *
 * @param citation    原始引用文本
 * @param status      核验状态（四态）
 * @param matchedTitle 命中的真实文献标题（VERIFIED/SUSPICIOUS 时有值）
 * @param note        说明（SUSPICIOUS/UNVERIFIABLE/NOT_FOUND 时的原因）
 */
public record CitationCheck(String citation, CitationStatus status, String matchedTitle, String note) {

    public CitationCheck {
        citation = requireText(citation, "citation");
        status = status == null ? CitationStatus.UNVERIFIABLE : status;
        note = note == null ? "" : note;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
