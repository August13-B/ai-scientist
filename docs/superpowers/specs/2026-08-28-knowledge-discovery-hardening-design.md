# 知识发现模块独立收尾设计

## 目标

在不实现论文解析、向量数据库、百炼客户端、DAG 管线和其他 Agent 的前提下，把马艺萌负责的知识发现模块收口为可独立测试、证据可追溯、接入规则明确的组件。

## 范围

本次只修改 `KnowledgeDiscoveryAgent`、`KnowledgeDiscoveryModels`、`KnowledgeDiscoveryPrompts`、对应测试与接入文档，不增加第三方依赖。

不修改以下协作模块：

- 马梓涵负责的论文下载、解析与清洗；
- 丁贾峻负责的向量库与百炼客户端；
- 张睿负责的 DAG 管线与文献检索 Agent；
- 黄晴昀负责的方法库与假设生成 Agent；
- 其他成员负责的评估、实验设计和辩论 Agent。

## 证据规则

1. DOI、PMID、URL 按稳定格式生成来源标识。DOI 去掉 `doi:` 与 `https://doi.org/` 前缀并转为小写；PMID 去掉 `pmid:` 前缀；URL 去掉首尾空白。
2. 直接输入或 RAG 返回的论文按规范化来源标识去重，保留第一次出现的论文及原顺序。
3. 知识发现至少需要两篇不同来源论文，否则不能声称完成跨论文比较并立即返回清晰错误。
4. 证据提取阶段必须对每个输入来源恰好返回一次分析；遗漏、重复或虚构来源均失败。
5. 排序阶段至少返回一个 Research Gap；每个 Gap 必须包含真实证据来源。
6. 最终 `references` 必须覆盖所有 Gap 使用的来源，且不得包含输入之外的来源。

## 数据流

`DiscoveryRequest` 优先使用直接证据，否则复用 `RagSearchService.search("papers", question, topK)`。证据在第一次模型调用前完成来源规范化、去重与最小数量校验。三阶段模型调用保持不变：证据提取、跨论文比较、Research Gap 排序。

提示词要求证据提取覆盖每个来源恰好一次，并要求排序至少给出一个带引用的 Gap。Java 侧仍执行确定性校验，不能依赖模型自觉遵守提示词。

## 错误处理

- 有效论文不足两篇：`IllegalArgumentException`，模型不应被调用；
- 阶段 JSON 无效：`IllegalStateException`，消息包含阶段名称；
- 提取覆盖不完整或来源重复：`IllegalStateException`；
- Gap 为空、引用虚构或引用覆盖不完整：`IllegalStateException`。

## 测试与文档

测试使用真实数据模型与知识发现服务，仅模拟尚未实现的百炼和 RAG 外部边界。新增测试覆盖来源规范化、证据去重、少于两篇、提取遗漏与重复、空 Gap、引用覆盖不完整及 Markdown JSON 代码块。

`ai-service/README.md` 和 `docs/agents.md` 补充最小调用示例、来源规范与失败条件。文档只描述知识发现消费侧契约，不替向量库团队定义 collection/schema。

## 完成标准

- 所有 `ai-service` 自动测试通过；
- `git diff --check` 通过；
- 工作仅位于马艺萌功能分支；
- 提交推送至现有 GitHub PR；
- PR 无合并冲突。
