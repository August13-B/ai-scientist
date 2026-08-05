package com.aiscientist.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI Scientist 多智能体服务启动类。
 * 职责：七 Agent DAG 管线、四库 RAG 检索、百炼 Qwen 调用、SSE 事件流。
 */
@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }
}
