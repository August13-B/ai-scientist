package com.aiscientist.ai.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CitationVerifier 测试：核验四态区分。
 */
class CitationVerifierTest {

    private ExternalLookup lookup;
    private CitationVerifier verifier;

    @BeforeEach
    void setUp() {
        lookup = mock(ExternalLookup.class);
        verifier = new CitationVerifier(lookup);
    }

    @Test
    void correctDoiShouldBeVerified() {
        when(lookup.findByDoi("10.1038/nature14539"))
                .thenReturn(ExternalLookup.Result.found("Deep learning"));
        CitationCheck r = verifier.verify("10.1038/nature14539");
        assertEquals(CitationStatus.VERIFIED, r.status());
    }

    @Test
    void fakeDoiShouldBeNotFound() {
        when(lookup.findByDoi("10.9999/fake"))
                .thenReturn(ExternalLookup.Result.absent());
        CitationCheck r = verifier.verify("10.9999/fake");
        assertEquals(CitationStatus.NOT_FOUND, r.status());
    }

    @Test
    void networkErrorShouldBeUnverifiable() {
        when(lookup.findByDoi("10.1038/nature14539"))
                .thenReturn(ExternalLookup.Result.error());
        CitationCheck r = verifier.verify("10.1038/nature14539");
        assertEquals(CitationStatus.UNVERIFIABLE, r.status());
    }

    @Test
    void arxivFoundShouldBeVerified() {
        when(lookup.findByArxivId("1706.03762"))
                .thenReturn(ExternalLookup.Result.found("Attention Is All You Need"));
        CitationCheck r = verifier.verify("arXiv:1706.03762");
        assertEquals(CitationStatus.VERIFIED, r.status());
    }

    @Test
    void verifiedLocalUrlShouldBeAccepted() {
        String url = "https://www.usenix.org/conference/atc24/presentation/gu-yunfei";
        when(lookup.findBySourceId("url:" + url))
                .thenReturn(ExternalLookup.Result.found("APTN"));

        CitationCheck r = verifier.verify("url:" + url);

        assertEquals(CitationStatus.VERIFIED, r.status());
        assertEquals("APTN", r.matchedTitle());
    }

    @Test
    void unknownLocalUrlShouldBeNotFound() {
        String url = "https://example.com/not-in-library";
        when(lookup.findBySourceId("url:" + url))
                .thenReturn(ExternalLookup.Result.absent());

        CitationCheck r = verifier.verify(url);

        assertEquals(CitationStatus.NOT_FOUND, r.status());
    }

    @Test
    void uploadedLocalDocumentShouldBeVerifiedByExactSourceId() {
        String url = "localdoc://doc-abc/paper.pdf?library=papers&page=3&chunk=2&id=x";
        when(lookup.findBySourceId("url:" + url))
                .thenReturn(ExternalLookup.Result.found("paper.pdf · 第3页 · 分块2"));

        CitationCheck result = verifier.verify("url:" + url);

        assertEquals(CitationStatus.VERIFIED, result.status());
    }

    @Test
    void titleMismatchShouldBeSuspicious() {
        when(lookup.findByDoi("10.1038/nature14539"))
                .thenReturn(ExternalLookup.Result.found("Deep learning"));
        CitationCheck r = verifier.verify("Completely Different Title | 10.1038/nature14539");
        assertEquals(CitationStatus.SUSPICIOUS, r.status());
    }

    @Test
    void twoDifferentChineseTitlesShouldNotMatch() {
        when(lookup.findByDoi("10.1038/nature14539"))
                .thenReturn(ExternalLookup.Result.found("深度学习"));
        CitationCheck r = verifier.verify("机器学习 | 10.1038/nature14539");
        assertEquals(CitationStatus.SUSPICIOUS, r.status(),
                "两个不同的中文标题不能匹配，应判 SUSPICIOUS");
    }

    @Test
    void normalizeKeepsUnicodeLetters() {
        assertFalse(CitationVerifier.fuzzyEqual("深度学习", "机器学习"));
        assertTrue(CitationVerifier.fuzzyEqual("Deep Learning", "deep-learning"));
    }

    @Test
    void noIdentifierShouldBeUnverifiable() {
        when(lookup.findByTitle(anyString())).thenReturn(null);
        CitationCheck r = verifier.verify("某篇没有标识符的文献标题");
        assertEquals(CitationStatus.UNVERIFIABLE, r.status());
    }
}
