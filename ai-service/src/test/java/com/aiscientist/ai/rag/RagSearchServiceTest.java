package com.aiscientist.ai.rag;

import com.aiscientist.ai.agent.KnowledgeDiscoveryModels.PaperEvidence;
import com.aiscientist.ai.llm.BailianClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** RagSearchService 纯逻辑测试：Chroma 响应映射 / source_id 解析 / 库切换提示。 */
class RagSearchServiceTest {

    private static final String CHROMA_RESPONSE = """
            {
              "ids": [["chunk-1", "chunk-2"]],
              "metadatas": [[
                {"source_id": "doi:10.1000/a", "title": "论文 A", "year": 2025,
                 "venue": "CVPR", "authors": "张三,李四"},
                {"source_id": "pmid:12345", "title": "论文 B", "year": 2024,
                 "venue": "", "authors": ""}
              ]],
              "documents": [["田间图像实验正文", "小样本实验正文"]],
              "distances": [[0.12, 0.35]]
            }
            """;

    @Test
    void mapsChromaResponseToPaperEvidence() throws Exception {
        RagSearchService service = service("chroma");

        List<PaperEvidence> papers = service.toPaperEvidence(
                new ObjectMapper().readTree(CHROMA_RESPONSE));

        assertEquals(2, papers.size());
        PaperEvidence first = papers.get(0);
        assertEquals("论文 A", first.title());
        assertEquals("田间图像实验正文", first.content());
        assertEquals(List.of("张三", "李四"), first.authors());
        assertEquals(2025, first.year());
        assertEquals("10.1000/a", first.doi());
        assertEquals("doi:10.1000/a", first.sourceId());

        PaperEvidence second = papers.get(1);
        assertEquals("12345", second.pmid());
        assertEquals("pmid:12345", second.sourceId());
        assertNull(second.doi());
        assertEquals(List.of(), second.authors());
    }

    @Test
    void mapsUploadedLocalDocumentSource() throws Exception {
        String response = """
                {"ids":[["chunk-1"]],
                 "metadatas":[[{"source_id":"url:localdoc://doc-abc/paper.pdf?library=papers&page=3&chunk=2&id=x",
                                  "title":"paper.pdf · 第3页 · 分块2","source_file":"paper.pdf","page":3}]],
                 "documents":[["原论文第三页正文"]]}
                """;

        PaperEvidence paper = service("chroma").toPaperEvidence(
                new ObjectMapper().readTree(response)).get(0);

        assertEquals("paper.pdf · 第3页 · 分块2", paper.title());
        assertTrue(paper.url().startsWith("localdoc://doc-abc/"));
        assertTrue(paper.sourceId().startsWith("url:localdoc://doc-abc/"));
    }

    @Test
    void hybridMergeKeepsCuratedAndUploadedEvidence() {
        PaperEvidence curatedA = paper("精选 A", "10.1000/a", null);
        PaperEvidence curatedB = paper("精选 B", "10.1000/b", null);
        PaperEvidence curatedC = paper("精选 C", "10.1000/c", null);
        PaperEvidence localA = paper("原文 A", null, "localdoc://doc-a/file.pdf?page=1");
        PaperEvidence localB = paper("原文 B", null, "localdoc://doc-b/file.pdf?page=2");

        List<PaperEvidence> result = RagSearchService.hybridMerge(
                List.of(curatedA, curatedB, curatedC), List.of(localA, localB), 5);

        assertEquals(5, result.size());
        assertTrue(result.stream().anyMatch(item -> item.sourceId().startsWith("doi:")));
        assertTrue(result.stream().anyMatch(item -> item.sourceId().startsWith("url:localdoc:")));
    }

    @Test
    void parsesSourceIdIntoRawIdentifiers() {
        assertEquals("10.1000/a", RagSearchService.doiOf("doi:10.1000/a"));
        assertNull(RagSearchService.doiOf("pmid:123"));
        assertEquals("123", RagSearchService.pmidOf("pmid:123"));
        assertEquals("https://x.org/a", RagSearchService.urlOf("url:https://x.org/a"));
        assertNull(RagSearchService.urlOf("doi:10.1"));
    }

    @Test
    void splitsAuthorsOnCommaAndSkipsBlanks() {
        assertEquals(List.of(), RagSearchService.splitAuthors(""));
        assertEquals(List.of("张三", "李四"), RagSearchService.splitAuthors("张三, 李四"));
        assertEquals(List.of("A"), RagSearchService.splitAuthors("A,,"));
    }

    @Test
    void rejectsMilvusModeWithClearMessage() {
        RagSearchService service = service("milvus");

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
                () -> service.search("papers", "研究问题", 5));

        assertTrue(error.getMessage().contains("milvus"));
    }

    @Test
    void rejectsUnknownVectorDbMode() {
        RagSearchService service = service("weird");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.search("papers", "研究问题", 5));

        assertTrue(error.getMessage().contains("weird"));
    }

    private static RagSearchService service(String vectorDb) {
        return new RagSearchService(vectorDb, "localhost", 8000, 60, false, mock(BailianClient.class));
    }

    @Test
    void mockModeReturnsBuiltinSamplesWithoutVectorDb() {
        // 本地调试：RAG_MOCK_SAMPLES=true 时不连 Chroma，直接返回内置样例论文
        RagSearchService mockService = new RagSearchService(
                "chroma", "localhost", 8000, 60, true, mock(BailianClient.class));

        List<PaperEvidence> papers = mockService.search("papers", "任何问题", 3);

        assertEquals(3, papers.size());
        assertEquals("doi:10.21275/sr231218142714", papers.get(0).sourceId());
        assertTrue(papers.stream().allMatch(paper -> paper.doi() != null));
    }

    @Test
    void mockModeHonorsTopKAndNeverReturnsMoreThanSamples() {
        RagSearchService mockService = new RagSearchService(
                "chroma", "localhost", 8000, 60, true, mock(BailianClient.class));

        assertEquals(4, mockService.search("evidence", "问题", 99).size());
        assertEquals(1, mockService.search("methods", "问题", 1).size());
    }

    private static PaperEvidence paper(String title, String doi, String url) {
        return new PaperEvidence(title, "正文", List.of(), 2026, doi, null, url);
    }
}
