package com.aiscientist.ai.agent;

import com.aiscientist.ai.llm.BailianClient;
import com.aiscientist.ai.pipeline.PipelineModels.QuestionQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ① 问题理解 Agent（张睿负责）。
 *
 * <p>将用户自然语言科研问题拆解为结构化子查询：识别领域标签、关键概念、
 * 已知条件与待求解变量，输出 {@link QuestionQuery} 供 ② 文献检索逐条检索、
 * ③ 知识发现与 ④ 假设生成取域信息。</p>
 *
 * <p>校验（Jackson 反序列化 record 不触发 compact 构造器校验，故手动把关）：</p>
 * <ul>
 *   <li>{@code subQueries}：问题拆解子查询，非空且 ≤8 条（Prompt 要求 3~5 条），每条非空；</li>
 *   <li>{@code keyConcepts}：关键概念非空（1~10 个）；</li>
 *   <li>{@code knownConditions} / {@code targetVariables}：识别不出可为空；</li>
 *   <li>{@code domain}：为空时默认「通用科研」（不写死 SSD 等学科）。</li>
 * </ul>
 *
 * <p>输入输出契约见 {@link QuestionQuery}（PipelineModels）。</p>
 */
@Service
public class ProblemUnderstandingAgent {

    /** 问题拆解为重任务（管线首步，出错全链路偏）：走 Qwen-Max 分级 */
    private static final String MODEL = "qwen-max";

    private static final String SYSTEM_PROMPT = """
            你是科研问题理解 Agent。把用户提出的科研问题拆解为结构化子查询。
            要求：
            1. subQueries：拆解为 3 至 5 个面向检索/分析的子问题，覆盖问题的不同侧面，彼此不重复；
            2. domain：识别领域标签（如 农业人工智能 / 生物医学 / 计算机视觉 / 通用科研），不确定时用「通用科研」；
            3. keyConcepts：提取 3 至 8 个关键概念/术语（中英文均可，便于检索召回）；
            4. knownConditions：已知条件/给定约束，没有则为空数组；
            5. targetVariables：待求解/待验证的目标变量，没有则为空数组。
            只返回一个 JSON（不要 markdown 代码块）：
            {"originalQuestion":"原问题原文","domain":"领域","subQueries":["..."],
             "keyConcepts":["..."],"knownConditions":["..."],"targetVariables":["..."]}
            """;

    private final BailianClient bailianClient;
    private final ObjectMapper objectMapper;

    public ProblemUnderstandingAgent(BailianClient bailianClient, ObjectMapper objectMapper) {
        this.bailianClient = bailianClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 拆解科研问题为结构化子查询。
     *
     * @param question 用户输入的科研问题（自然语言）
     * @return 规范化后的 {@link QuestionQuery}（触发 compact 构造器校验与不可变列表）
     * @throws IllegalStateException 模型返回无效 JSON 或未通过结构校验
     */
    public QuestionQuery understand(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        String trimmed = question.trim();

        QuestionQuery parsed;
        try {
            String response = bailianClient.chat(MODEL, SYSTEM_PROMPT, trimmed);
            parsed = objectMapper.readValue(stripCodeFence(response), QuestionQuery.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("问题理解 Agent 返回了无效 JSON", exception);
        }
        validate(parsed, trimmed);

        // 规范化重建：触发 QuestionQuery compact 构造器（非空校验 + 不可变列表），domain 空默认「通用科研」
        String domain = parsed.domain() == null || parsed.domain().isBlank()
                ? "通用科研" : parsed.domain().trim();
        return new QuestionQuery(
                trimmed,
                domain,
                List.copyOf(parsed.subQueries()),
                List.copyOf(parsed.keyConcepts()),
                List.copyOf(parsed.knownConditions() == null ? List.of() : parsed.knownConditions()),
                List.copyOf(parsed.targetVariables() == null ? List.of() : parsed.targetVariables())
        );
    }

    /** 结构校验：subQueries / keyConcepts 非空有界，其余字段容缺 */
    private void validate(QuestionQuery query, String originalQuestion) {
        if (query == null) {
            throw new IllegalStateException("问题理解结果不能为空");
        }
        if (isBlank(query.originalQuestion())) {
            throw new IllegalStateException("问题理解结果缺少 originalQuestion");
        }
        List<String> subQueries = query.subQueries() == null ? List.of() : query.subQueries();
        if (subQueries.isEmpty() || subQueries.size() > 8) {
            throw new IllegalStateException("问题理解结果必须包含 1 至 8 条子查询（实际 "
                    + subQueries.size() + " 条）");
        }
        if (subQueries.stream().anyMatch(this::isBlank)) {
            throw new IllegalStateException("问题理解结果包含空子查询");
        }
        List<String> concepts = query.keyConcepts() == null ? List.of() : query.keyConcepts();
        if (concepts.isEmpty() || concepts.size() > 10) {
            throw new IllegalStateException("问题理解结果必须包含 1 至 10 个关键概念（实际 "
                    + concepts.size() + " 个）");
        }
        if (concepts.stream().anyMatch(this::isBlank)) {
            throw new IllegalStateException("问题理解结果包含空关键概念");
        }
        // domain 容缺：为空时在 understand() 中默认「通用科研」
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 剥离模型常见 markdown 代码块包裹（```json ... ```） */
    private String stripCodeFence(String response) {
        if (response == null) {
            throw new IllegalArgumentException("model response must not be null");
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            int firstLineEnd = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
                throw new IllegalArgumentException("invalid JSON code fence");
            }
            json = json.substring(firstLineEnd + 1, lastFence).trim();
        }
        return json;
    }
}
