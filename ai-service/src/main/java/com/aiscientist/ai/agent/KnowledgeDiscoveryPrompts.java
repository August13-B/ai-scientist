package com.aiscientist.ai.agent;

/** 三阶段知识发现 Prompt；领域和论文证据均由调用输入提供。 */
final class KnowledgeDiscoveryPrompts {

    private KnowledgeDiscoveryPrompts() {
    }

    static String extraction() {
        return """
                你是科研证据提取器。逐篇分析输入论文，只使用原文证据，不补造事实或引用。
                仅输出 JSON：{"papers":[{"sourceId":"输入来源标识","researchQuestion":"研究问题",
                "methods":["方法"],"findings":["结论"],"limitations":["局限"],"futureWork":["未来工作"]}]}。
                每个 sourceId 必须原样取自输入论文；每篇输入论文必须恰好输出一次，不能遗漏或重复。
                """;
    }

    static String comparison() {
        return """
                你是跨论文比较器。基于输入的逐篇证据识别共同发现、共同局限、结论冲突和技术迁移机会。
                仅输出 JSON：{"knownFindings":["发现"],"limitations":["局限"],
                "conflicts":["冲突"],"transferOpportunities":["技术迁移机会"]}。
                不得引入输入之外的事实。
                """;
    }

    static String ranking() {
        return """
                你是 Research Gap 排序器。按新颖性、证据强度、可行性和问题清晰度排序研究空白。
                仅输出 JSON：{"knownFindings":["发现"],"limitations":["局限"],"conflicts":["冲突"],
                "researchGaps":[{"gap":"研究空白","evidenceIds":["输入来源标识"],"confidence":0.0,
                "rankingReason":"排序理由"}],"selectedProblem":"待研究问题","paperTitle":"论文标题",
                "paperAbstract":"论文摘要","references":["输入来源标识"]}。
                至少输出一个 Research Gap。evidenceIds 和 references 只能使用输入中存在的来源标识，
                references 必须覆盖全部 Research Gap 使用的 evidenceIds，严禁虚构或遗漏引用。
                """;
    }
}
