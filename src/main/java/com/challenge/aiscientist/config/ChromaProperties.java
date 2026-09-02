package com.challenge.aiscientist.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chroma")
public record ChromaProperties(boolean enabled, String baseUrl, String tenant, String database, String collection) { }
