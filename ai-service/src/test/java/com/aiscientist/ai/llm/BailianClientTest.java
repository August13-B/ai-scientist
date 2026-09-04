package com.aiscientist.ai.llm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BailianClient 纯逻辑测试：响应解析 / 模型别名路由 / 密钥校验（不发起真实 HTTP）。 */
class BailianClientTest {

    private static final String CHAT_RESPONSE = """
            {"choices":[{"message":{"role":"assistant","content":"{\\"papers\\":[]}"}}]}
            """;

    private static final String EMBEDDING_RESPONSE = """
            {"data":[{"embedding":[0.1,0.2,0.3]},{"embedding":[-1.0,0.0,0.5]}]}
            """;

    @Test
    void parsesChatContentFromChoices() {
        assertEquals("{\"papers\":[]}", BailianClient.parseChatContent(CHAT_RESPONSE));
    }

    @Test
    void rejectsChatResponseWithoutContent() {
        assertThrows(IllegalArgumentException.class,
                () -> BailianClient.parseChatContent("{\"choices\":[]}"));
        assertThrows(IllegalArgumentException.class,
                () -> BailianClient.parseChatContent("{\"choices\":[{\"message\":{}}]}"));
        assertThrows(IllegalArgumentException.class,
                () -> BailianClient.parseChatContent("not-json"));
    }

    @Test
    void parsesEmbeddingsIntoVectorList() {
        List<List<Double>> vectors = BailianClient.parseEmbeddings(EMBEDDING_RESPONSE);

        assertEquals(2, vectors.size());
        assertEquals(List.of(0.1, 0.2, 0.3), vectors.get(0));
        assertEquals(List.of(-1.0, 0.0, 0.5), vectors.get(1));
    }

    @Test
    void rejectsMalformedEmbeddingResponse() {
        assertThrows(IllegalArgumentException.class,
                () -> BailianClient.parseEmbeddings("{\"data\":[{\"embedding\":\"oops\"}]}"));
        assertThrows(IllegalArgumentException.class,
                () -> BailianClient.parseEmbeddings("broken"));
    }

    @Test
    void resolvesModelAliasesToConfiguredModels() {
        BailianClient client = client("key");

        assertEquals("cfg-heavy", client.resolveModel("qwen-max"));
        assertEquals("cfg-heavy", client.resolveModel("qwen-heavy"));
        assertEquals("cfg-light", client.resolveModel("qwen-plus"));
        assertEquals("cfg-light", client.resolveModel("qwen-light"));
        assertEquals("cfg-turbo", client.resolveModel("qwen-turbo"));
        assertEquals("unknown-model", client.resolveModel("unknown-model"));
    }

    @Test
    void rejectsChatWhenApiKeyMissing() {
        BailianClient client = client("");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> client.chat("qwen-max", "系统", "用户"));

        assertTrue(error.getMessage().contains("ALIYUN_BAILIAN_API_KEY"));
    }

    private static BailianClient client(String apiKey) {
        return new BailianClient(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/",
                apiKey,
                0.7,
                60,
                "cfg-heavy",
                "cfg-light",
                "cfg-turbo",
                "text-embedding-v4");
    }
}
