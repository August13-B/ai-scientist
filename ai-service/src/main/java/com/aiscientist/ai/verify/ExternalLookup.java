package com.aiscientist.ai.verify;

/**
 * 外部文献检索源接口（Crossref / arXiv / PubMed 的统一抽象）。
 *
 * <p>生产实现通过 HttpClient 直连公共学术接口；测试可 mock 本接口，
 * 覆盖「正确引用通过 / 虚假引用 NOT_FOUND / 网络异常 UNVERIFIABLE」。</p>
 */
public interface ExternalLookup {

    /** 三态查询结果 */
    record Result(Status status, String title) {
        public enum Status {
            /** 确认存在 */
            FOUND,
            /** 接口正常返回，确认不存在 */
            ABSENT,
            /** 网络/接口异常 */
            ERROR
        }

        public static Result found(String title) {
            return new Result(Status.FOUND, title);
        }

        public static Result absent() {
            return new Result(Status.ABSENT, null);
        }

        public static Result error() {
            return new Result(Status.ERROR, null);
        }
    }

    /** 按 DOI 精确查询 */
    Result findByDoi(String doi);

    /** 按 arXiv ID 精确查询 */
    Result findByArxivId(String arxivId);

    /** 按 PMID 精确查询 */
    Result findByPmid(String pmid);

    /** 按本地四库的规范化来源标识精确查询（如 url:https://...） */
    Result findBySourceId(String sourceId);

    /** 按标题查询（本地 RAG 库），返回命中的真实标题或 null */
    String findByTitle(String title);
}
