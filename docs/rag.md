# 四库 RAG 设计

> 更新时间：2026-09-01（第二版）
> 本文档固化设计原则、分工与**灌库实现契约**。向量化模型/分块策略/collection 命名已随灌库脚本落地；检索侧字段对齐见本文档第 5 节。

## 0. 实现状态（2026-09-01 更新）

| 模块 | 状态 | 位置 |
|---|---|---|
| 四库灌库脚本 | ✅ **已实现** | `data/scripts/ingest_{papers,methods,datasets,evidence}.py` |
| 灌库公共模块 | ✅ 已实现 | `data/scripts/rag_common.py`（连接+Embedding）、`rag_base.py`（基类） |
| 向量化 | ✅ 已定为百炼 DashScope Embedding API | `EMBEDDING_PROVIDER=dashscope`，模型 `text-embedding-v3` |
| 向量库 | ✅ Chroma（开发）/ Milvus（生产）双支持 | `VECTOR_DB` 切换；docker-compose 已编排 |
| 检索服务（RagSearchService） | ⚠️ 待实现 | `ai-service` 的 `rag/RagSearchService.java`（TODO） |
| 数据源（raw/processed） | ⚠️ 待马梓涵产数据 | 按 JSONL 格式产出（见第 4 节） |

> ⚠️ **协作注意**：Agent 接入时如需 RAG 检索，检索结果字段必须与灌库脚本写入的元数据对齐（见第 5 节），否则 `objectMapper.convertValue` 转换失败。

## 1. 四库总览

| 知识库 | 内容范围 | 主要服务 Agent | 负责人 |
|---|---|---|---|
| 论文库 | 目标领域论文摘要与全文向量化索引 | 文献检索、知识发现、假设生成、评估 | 马艺萌（构建）/ 马梓涵（数据） |
| 方法库 | 经典 ML/DL 方法、统计方法、实验范式、评估指标 | 知识发现、假设生成、实验设计 | 黄晴昀 |
| 数据集库 | 各领域公开数据集元信息（名称、特征、样本量、出处） | 评估、实验设计 | 钱思妤 |
| 证据库 | 已证实的科学事实三元组（A→B，附带文献支撑） | 文献检索、假设生成、评估 | 王婉莹 |

## 2. 技术选型

| 组件 | 选型 | 说明 |
|---|---|---|
| 向量数据库 | Milvus（生产）/ Chroma（开发） | `VECTOR_DB` 环境变量切换；Python SDK 已接入灌库脚本 |
| 文档解析 | pdfplumber + Grobid | 学术 PDF 结构化：提取标题、摘要、章节、参考文献 |
| 向量化模型 | **DashScope Embedding API**（text-embedding-v3） | 已定稿：灌库脚本通过百炼在线向量化，维度 1024（可配） |
| 分块策略 | RecursiveCharacterTextSplitter 风格 | 语义保持分块，`chunk_size=512, overlap=64`（脚本参数可覆盖） |
| 检索策略 | 混合检索：向量相似度 + BM25 | 兼顾语义匹配与精确术语匹配（检索侧 TODO） |

## 3. 四库构建流程（骨架）

### 3.1 论文库（马艺萌负责，马梓涵提供数据）

```
选题确定 → 爬取/下载领域论文 → Grobid 结构化解析 → 按「摘要+章节」分块
  → BGE-M3 向量化 → 存入向量库（附带元数据：作者、年份、会议/期刊、引用数）
```

### 3.2 方法库（黄晴昀负责）

```
整理领域经典方法论文 → 提取「方法名-使用场景-实现步骤-评估结果」结构化条目 → 向量化存入
```

### 3.3 数据集库（钱思妤负责）

```
收集领域公开数据集 → 提取「名称-特征维度-样本量-标注方式-来源URL」元信息 → 存入结构化索引
```

### 3.4 证据库（王婉莹负责）

```
从高引论文提取「已知事实三元组（Subject-Predicate-Object）」→ 附带源文献 PMID/DOI → 向量化存入
```

## 4. 混合检索设计

```
查询向量化（DashScope Embedding API）
  ├── 向量检索：Top-K 语义相似结果
  ├── BM25 关键词检索：精确术语命中
  └── 融合重排（RRF 或加权）→ 输出最终 Top-K
```

- 检索结果附带来源元数据，供评估 Agent 做引用真实性核验
- 论文库/证据库**必须包含来源标识**（source_id: doi:xxx / pmid:xxx / url:xxx）以支撑幻觉检测

## 5. 向量化与分块约定

- 向量化：百炼 DashScope Embedding API（`EMBEDDING_MODEL=text-embedding-v3`，维度 1024）
- 分块：`chunk_size=512, overlap=64`（灌库脚本 `--chunk-size/--chunk-overlap` 可覆盖）
- 中文场景建议保留原始段落标题作为分块边界

## 6. 灌库数据契约（JSONL，马梓涵产出格式）

灌库脚本从 `data/processed/*.jsonl` 读取，**每行一条 JSON**，必带来源标识：

| 库 | 文件 | 必填字段 | 来源标识 |
|---|---|---|---|
| 论文库 | `papers.jsonl` | title / abstract / content | doi（或 pmid / url） |
| 方法库 | `methods.jsonl` | method_name / scenario / steps / evaluation | source_doi（或 source_pmid / source_url） |
| 数据集库 | `datasets.jsonl` | name / features / samples / annotation | url |
| 证据库 | `evidence.jsonl` | subject / predicate / object | source_pmid（或 source_doi / source_url） |

collection 命名：`papers` / `methods` / `datasets` / `evidence`（脚本内常量，可 `--collection` 覆盖）。

## 7. 检索契约对齐（Agent 接入必读）

- 灌库脚本写入的每条向量元数据包含 `source_id`、`text` 及各库专有字段（title/year/venue 等）
- `ai-service` 的 `RagSearchService.search(knowledgeBase, query, topK)` 实现时，返回对象字段必须与 `PaperEvidence` 对齐：`title / content / doi / pmid / url / authors / year`（马艺萌代码用 `objectMapper.convertValue` 转换，字段名不匹配会转换失败）
- 四库类型字符串：`"papers" / "methods" / "datasets" / "evidence"`

## 8. 数据质量要求（马梓涵）

- 数据清洗质量直接决定 Agent 输出水平，是系统「源头活水」
- 清洗产出：论文库/方法库/数据库/证据库四份**合规真实**的高质量 JSONL 数据集
- 多模态实测数据（实验记录、观测数据）结构化后与文本知识一起灌库

> ⚠️ 数据来源合规性：仅使用公开、可溯源的数据集与文献，严禁虚构（赛题硬性要求）。
