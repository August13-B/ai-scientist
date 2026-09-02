package com.aiscientist.ai.verify;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用核验器（钱思妤负责，核心质量关卡）。
 *
 * <p>逐条反查真实文献，输出四态核验结果。核验优先级：</p>
 * <ol>
 *   <li>结构化标识符精确核验（最强证据）：DOI → Crossref；arXiv ID → arXiv 官方接口；PMID → NCBI E-utilities；</li>
 *   <li>标题反向比对：本地 RAG 论文库 / 证据库；</li>
 *   <li>自洽性检查：标识符命中但标题不符 → SUSPICIOUS。</li>
 * </ol>
 *
 * <p>网络失败（超时/限流/接口异常）判 UNVERIFIABLE，不冤枉真实论文。</p>
 */
public final class CitationVerifier {

    private static final Pattern DOI = Pattern.compile("10\\.\\d{4,9}/[-._;()/:a-zA-Z0-9]+");
    private static final Pattern ARXIV = Pattern.compile("arXiv[:\\s]*(\\d{4}\\.\\d{4,5})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PMID = Pattern.compile("(?i)\\bpmid[:\\s]*(\\d{6,9})\\b");

    private final ExternalLookup lookup;

    /**
     * @param lookup 外部文献检索源（Crossref/arXiv/PubMed），可由测试 mock
     */
    public CitationVerifier(ExternalLookup lookup) {
        this.lookup = lookup;
    }

    /** 核验一条引用 */
    public CitationCheck verify(String citation) {
        String raw = citation == null ? "" : citation.trim();

        String doi = extractDoi(raw);
        String arxivId = extractArxiv(raw);
        String pmid = extractPmid(raw);

        if (doi != null) {
            return verifyDoi(doi, raw);
        }
        if (arxivId != null) {
            return verifyArxiv(arxivId, raw);
        }
        if (pmid != null) {
            return verifyPmid(pmid, raw);
        }

        String title = titleOf(raw);
        if (title != null && !title.isBlank()) {
            String matched = lookup.findByTitle(title);
            if (matched == null) {
                return new CitationCheck(raw, CitationStatus.UNVERIFIABLE, null,
                        "标题未在本地文献库命中（可能未覆盖），需人工确认");
            }
            if (!fuzzyEqual(matched, title)) {
                return new CitationCheck(raw, CitationStatus.SUSPICIOUS, matched,
                        "标题与真实文献不符");
            }
            return new CitationCheck(raw, CitationStatus.VERIFIED, matched, "命中真实文献");
        }

        return new CitationCheck(raw, CitationStatus.UNVERIFIABLE, null,
                "缺少 DOI/arXiv/PMID/标题，无法核验");
    }

    private CitationCheck verifyDoi(String doi, String raw) {
        ExternalLookup.Result r = lookup.findByDoi(doi);
        if (r.status() == ExternalLookup.Result.Status.ERROR) {
            return new CitationCheck(raw, CitationStatus.UNVERIFIABLE, null,
                    "Crossref 查询异常（超时/限流/网络），请稍后重试或人工确认");
        }
        if (r.status() == ExternalLookup.Result.Status.ABSENT) {
            return new CitationCheck(raw, CitationStatus.NOT_FOUND, null,
                    "DOI " + doi + " 经 Crossref 确认不存在，疑似虚构");
        }
        String title = titleOf(raw);
        if (title != null && !title.isBlank() && !fuzzyEqual(r.title(), title)) {
            return new CitationCheck(raw, CitationStatus.SUSPICIOUS, r.title(),
                    "DOI 命中，但提供标题与真实标题不符：" + r.title());
        }
        return new CitationCheck(raw, CitationStatus.VERIFIED, r.title(), "命中真实文献");
    }

    private CitationCheck verifyArxiv(String arxivId, String raw) {
        ExternalLookup.Result r = lookup.findByArxivId(arxivId);
        if (r.status() == ExternalLookup.Result.Status.ERROR) {
            return new CitationCheck(raw, CitationStatus.UNVERIFIABLE, null,
                    "arXiv 查询异常（超时/限流/网络），请稍后重试或人工确认");
        }
        if (r.status() == ExternalLookup.Result.Status.ABSENT) {
            return new CitationCheck(raw, CitationStatus.NOT_FOUND, null,
                    "arXiv " + arxivId + " 经官方接口确认不存在，疑似虚构");
        }
        String title = titleOf(raw);
        if (title != null && !title.isBlank() && !fuzzyEqual(r.title(), title)) {
            return new CitationCheck(raw, CitationStatus.SUSPICIOUS, r.title(),
                    "arXiv 命中，但提供标题与真实标题不符：" + r.title());
        }
        return new CitationCheck(raw, CitationStatus.VERIFIED, r.title(), "命中真实文献");
    }

    private CitationCheck verifyPmid(String pmid, String raw) {
        ExternalLookup.Result r = lookup.findByPmid(pmid);
        if (r.status() == ExternalLookup.Result.Status.ERROR) {
            return new CitationCheck(raw, CitationStatus.UNVERIFIABLE, null,
                    "PubMed 查询异常（超时/限流/网络），请稍后重试或人工确认");
        }
        if (r.status() == ExternalLookup.Result.Status.ABSENT) {
            return new CitationCheck(raw, CitationStatus.NOT_FOUND, null,
                    "PMID " + pmid + " 经 PubMed 确认不存在，疑似虚构");
        }
        return new CitationCheck(raw, CitationStatus.VERIFIED, r.title(), "命中真实文献");
    }

    /** 批量核验，返回逐条结果 */
    public List<CitationCheck> verifyAll(List<String> citations) {
        return citations == null ? List.of() : citations.stream().map(this::verify).toList();
    }

    // ==================== 文本工具 ====================

    /** 保留 Unicode 字母和数字，避免中文标题被删空后误判为相同 */
    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    static boolean fuzzyEqual(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        return na.equals(nb) || na.contains(nb) || nb.contains(na);
    }

    /** 引用里 "|" 前的部分视为标题；纯标识符则返回 null */
    static String titleOf(String citation) {
        if (citation == null || citation.isBlank()) {
            return null;
        }
        String first = citation.split("\\|")[0].trim();
        if (DOI.matcher(first).matches() || ARXIV.matcher(first).matches() || PMID.matcher(first).matches()) {
            return null;
        }
        return first.isEmpty() ? null : first;
    }

    static String extractDoi(String raw) {
        Matcher m = DOI.matcher(raw == null ? "" : raw);
        return m.find() ? m.group() : null;
    }

    static String extractArxiv(String raw) {
        Matcher m = ARXIV.matcher(raw == null ? "" : raw);
        return m.find() ? m.group(1) : null;
    }

    static String extractPmid(String raw) {
        Matcher m = PMID.matcher(raw == null ? "" : raw);
        return m.find() ? m.group(1) : null;
    }
}
