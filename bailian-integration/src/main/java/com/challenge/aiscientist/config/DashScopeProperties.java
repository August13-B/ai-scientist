package com.challenge.aiscientist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dashscope")
public record DashScopeProperties(String baseUrl, String apiKey, String chatModel, String embeddingModel) { }
