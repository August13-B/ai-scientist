package com.aiscientist.ai.wangwanying.experiment;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class BailianConfiguration {
    @Bean
    public ChatModel experimentChatModel(
            @Value("${jiebang.agent.experiment-design.llm.api-key:}") String apiKey,
            @Value("${jiebang.agent.experiment-design.llm.base-url}") String baseUrl,
            @Value("${jiebang.agent.experiment-design.llm.model}") String model,
            @Value("${jiebang.agent.experiment-design.llm.temperature:0.2}") double temperature) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("缺少QWEN_API_KEY，请在IDEA运行配置的Environment variables中设置");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey.trim())
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .responseFormat("json_object")
                .timeout(Duration.ofSeconds(90))
                .maxRetries(2)
                .build();
    }
}