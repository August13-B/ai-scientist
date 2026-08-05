package com.aiscientist.ai.rag;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 四库 RAG 检索服务（骨架）。
 * 职责：论文库/方法库/数据集库/证据库的混合检索（向量相似度 + BM25），
 * 检索结果携带来源元数据（DOI/PMID）供评估 Agent 核验。
 * collection/schema 由数据引擎组设计时确定，见 docs/rag.md。
 * TODO（丁贾峻 + 各 Agent 负责人）：对接 Chroma/Milvus，实现混合检索与重排。
 */
@Service
public class RagSearchService {

    public List<Object> search(String knowledgeBase, String query, int topK) {
        // TODO: 按知识库类型检索（papers / methods / datasets / evidence）
        return List.of();
    }
}
