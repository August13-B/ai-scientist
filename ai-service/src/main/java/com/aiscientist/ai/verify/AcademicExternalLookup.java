package com.aiscientist.ai.verify;

import com.aiscientist.ai.rag.RagSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 外部文献检索源生产实现（Crossref / arXiv / PubMed + 本地 RAG）。
 *
 * <p>DOI/arXiv/PMID 通过 HttpClient 直连公共学术接口；标题通过团队统一的
 * {@link RagSearchService} 检索论文库/证据库。返回三态结果，
 * 网络异常统一判 ERROR（由上层映射为 UNVERIFIABLE）。</p>
 */
@Component
public class AcademicExternalLookup implements ExternalLookup {

    private static final String CROSSREF = "https://api.crossref.org/works/";
    private static final String ARXIV = "https://export.arxiv.org/api/query";
    private static final String PUBMED = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi";

    private final RagSearchService ragSearchService;
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AcademicExternalLookup(RagSearchService ragSearchService) {
        this.ragSearchService = ragSearchService;
    }

    @Override
    public Result findByDoi(String doi) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CROSSREF + doi))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "AI-Scientist/0.1 (challenge XH-202619; mailto:team@example.com)")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Result.absent();
            }
            if (response.statusCode() != 200) {
                return Result.error();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode titles = root.path("message").path("title");
            if (titles.isArray() && !titles.isEmpty()) {
                return Result.found(titles.get(0).asText());
            }
            return Result.absent();
        } catch (Exception e) {
            return Result.error();
        }
    }

    @Override
    public Result findByArxivId(String arxivId) {
        try {
            String url = ARXIV + "?search_query=id:" + arxivId + "&max_results=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "AI-Scientist/0.1 (challenge XH-202619; mailto:team@example.com)")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Result.error();
            }
            String title = parseArxivTitle(response.body());
            return title == null ? Result.absent() : Result.found(title);
        } catch (Exception e) {
            return Result.error();
        }
    }

    @Override
    public Result findByPmid(String pmid) {
        try {
            String url = PUBMED + "?db=pubmed&id=" + pmid
                    + "&retmode=json&tool=ai_scientist&email=team@example.com";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "AI-Scientist/0.1 (challenge XH-202619; mailto:team@example.com)")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Result.error();
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result").path(pmid);
            if (result.isMissingNode()) {
                return Result.absent();
            }
            String title = result.path("title").asText(null);
            return (title == null || title.isBlank()) ? Result.absent() : Result.found(title);
        } catch (Exception e) {
            return Result.error();
        }
    }

    @Override
    public Result findBySourceId(String sourceId) {
        try {
            for (String library : java.util.List.of("papers", "methods", "datasets", "evidence")) {
                var matched = ragSearchService.findBySourceId(library, sourceId);
                if (matched.isPresent()) {
                    return Result.found(matched.get().title());
                }
            }
            return Result.absent();
        } catch (Exception e) {
            return Result.error();
        }
    }

    @Override
    public String findByTitle(String title) {
        try {
            // 复用团队统一 RAG：在论文库检索标题，返回命中的真实标题
            var papers = ragSearchService.search("papers", title, 5);
            for (var p : papers) {
                if (p.title() != null && !p.title().isBlank()
                        && CitationVerifier.fuzzyEqual(p.title(), title)) {
                    return p.title();
                }
            }
            return null;
        } catch (Exception e) {
            // RAG 检索失败不视为虚构，交由上层判 UNVERIFIABLE
            return null;
        }
    }

    /** 从 arXiv Atom 响应提取第一篇 entry 标题 */
    private String parseArxivTitle(String xml) {
        try {
            var doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(
                            xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var entries = doc.getElementsByTagName("entry");
            if (entries.getLength() == 0) {
                return null;
            }
            var children = entries.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                var node = children.item(i);
                if ("title".equals(node.getNodeName()) && node.getTextContent() != null) {
                    return node.getTextContent().trim().replace("\n", " ");
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
