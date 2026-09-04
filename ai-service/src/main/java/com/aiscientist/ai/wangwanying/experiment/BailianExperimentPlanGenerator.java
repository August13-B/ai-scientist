package com.aiscientist.ai.wangwanying.experiment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiscientist.ai.wangwanying.evidence.Evidence;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BailianExperimentPlanGenerator implements ExperimentPlanGenerator {
    private static final int MAX_ATTEMPTS = 2;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public BailianExperimentPlanGenerator(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public GeneratedExperimentContent generate(ExperimentRequest request, List<Evidence> evidence) {
        String prompt = buildPrompt(request, evidence);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String response;
            try {
                response = chatModel.chat(prompt);
            } catch (RuntimeException error) {
                throw new IllegalStateException("百炼模型调用失败：" + error.getMessage(), error);
            }
            try {
                return objectMapper.readValue(extractJson(response), GeneratedExperimentContent.class);
            } catch (JsonProcessingException | IllegalArgumentException error) {
                lastError = new IllegalStateException(
                        "百炼返回的实验方案不是有效JSON，第" + attempt + "次解析失败：" + error.getMessage(), error);
                prompt += "\n上一次输出无法解析。请重新输出，禁止Markdown代码块，只能输出一个合法JSON对象。";
            }
        }
        throw lastError;
    }

    private String buildPrompt(ExperimentRequest request, List<Evidence> evidence) {
        try {
            String evidenceJson = objectMapper.writeValueAsString(evidence);
            return """
                    你是严谨的科研实验设计Agent。请根据科研假设和已检索证据设计可执行的对比实验。

                    强制规则：
                    1. 只能使用下方证据，不得虚构论文、DOI、PMID、数据集或实验结果。
                    2. datasets 只能选择 tags 中含 allowed-dataset 的证据；每项必须原样包含其 sourceTitle 和 sourceUri，不得编造名称或链接。
                    3. expectedResults只能写待验证的方向、范围和判定条件，不能声称实验已经完成。
                    4. Baseline必须可复现且公平控制数据和计算预算。
                    5. Metrics必须包括主要指标、统计不确定性、显著性/效应量、稳健性和资源成本。
                    6. 输出必须是中文，并且只能输出JSON对象，不要Markdown，不要解释文字。

                    JSON结构严格如下，每个字段必须是非空字符串数组：
                    {
                      "baselines": ["至少3项"],
                      "metrics": ["至少5项"],
                      "datasets": ["至少3项"],
                      "procedure": ["至少5项"],
                      "expectedResults": ["至少3项"],
                      "risks": ["至少3项"]
                    }

                    标题：%s
                    领域：%s
                    待验证假设：%s
                    主要结果变量：%s
                    允许使用的证据：%s
                    """.formatted(request.title(), request.domain(), request.hypothesis(), request.outcome(), evidenceJson);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("无法序列化检索证据", error);
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("百炼返回空内容");
        }
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("百炼响应中没有JSON对象");
        }
        return response.substring(start, end + 1);
    }
}
