package com.aiscientist.ai.llm;

import org.springframework.stereotype.Component;

/**
 * 阿里云百炼平台 Qwen 调用封装（骨架）。
 * 职责：统一封装百炼 OpenAI 兼容 API（Qwen-Max/Plus/Turbo），
 * 记录调用日志并留存调用凭证截图（赛题强制要求）。
 * TODO（丁贾峻）：实现流式/非流式调用、日志记录、模型分级路由
 * （重任务用 Qwen-Max，轻任务用 Qwen-Plus）。
 */
@Component
public class BailianClient {

    public String chat(String model, String systemPrompt, String userMessage) {
        // TODO: 调用百炼平台并返回结果
        return "TODO";
    }
}
