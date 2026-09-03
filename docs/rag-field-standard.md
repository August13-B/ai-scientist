# 四库 RAG 灌库字段标准（Field Contract Standard）

> 状态：**权威标准**（v1.0，2026-09-03 定稿）
> 适用范围：`data/scripts/ingest_*.py` 灌库数据（JSONL 输入）与 Chroma/Milvus 入库结构（metadata）
> 本文档是 `docs/rag.md` 第 6/7 节的**字段级落地标准**；两者冲突时以本文档为准，并回改 rag.md。
> 检查脚本：`data/scripts/validate_records.py`（灌库前数据质检，见 §8）。

---

## 0. 为什么需要这份标准（背景）

审核 PR #15 / #17 时发现一处**跨模块契约错配**：

- ai-service 的 `RagSearchService` 把**所有库**的检索结果统一映射为 `PaperEvidence`，而
  `PaperEvidence` 构造器强制 `title`、`content` **非空**（缺失即抛异常）；
- 但灌库侧 `methods`/`datasets`/`evidence` 三个库的 metadata **从不提供 `title`**（只有
  `method_name`/`name`/`subject` 等专有字段）；
- 后果：一旦灌入真实数据，`RagSearchService.search("methods"|"evidence")` 首次命中即抛
  `title must not be blank`，④ 假设生成等下游 Agent 必然失败（单测 mock 掩盖，CI 无法发现）。

修复路径 = **灌库侧补齐 `title` 等价字段**（见 §5），检索侧无需改动。

---

## 1. 总体原则

| 级别 | 含义 |
|---|---|
| **MUST** | 不满足则检索/灌库会失败或破坏幻觉检测，必须遵守 |
| **SHOULD** | 强烈建议，缺失影响检索质量或溯源能力 |

1. **每条记录 MUST 携带来源标识**（`doi` / `pmid` / `url` 至少一种）——幻觉检测「引用严禁虚构」的地基。
2. **metadata MUST 包含 `source_id` 与 `title`**（`title` 的语义按库派生，见 §5）——`RagSearchService`
   与 `PaperEvidence` 强制要求。
3. 入库 payload 统一为 `{id, text, metadata}`；`id` 重灌可复现，`metadata` 只放标量
   （列表等复杂值先序列化，且保证检索侧能还原）。
4. `source_id` 格式 MUST 为 `doi:xxx` / `pmid:xxx` / `url:xxx` 三者之一，见 §6。
5. 数据 MUST 合规真实、可公开溯源，**严禁虚构**（赛题红线）。

---

## 2. 四库 JSONL 输入契约（数据侧产出格式）

文件位于 `data/processed/*.jsonl`，每行一条 JSON。字段名与灌库脚本 `parse_record` 读取键**严格一致**。

### 2.1 论文库 `papers.jsonl`

| 字段 | 必填 | 说明 |
|---|---|---|
| `title` | MUST | 论文标题（检索 title 等价字段来源） |
| `abstract` | MUST | 摘要（可空串） |
| `content` | MUST | 正文/长摘要（可空串） |
| `authors` | SHOULD | 作者数组 `["A","B"]` |
| `year` | SHOULD | 发表年份（整数） |
| `venue` | SHOULD | 会议/期刊 |
| `doi` 或 `pmid` 或 `url` | MUST(至少一) | 来源标识 |

### 2.2 方法库 `methods.jsonl`

| 字段 | 必填 | 说明 |
|---|---|---|
| `method_name` | MUST | 方法名（检索 title 等价字段来源） |
| `scenario` | MUST | 适用场景 |
| `steps` | MUST | 实施步骤（字符串数组或 `；` 分隔字符串） |
| `evaluation` | SHOULD | 评估结果 |
| `source_doi` / `source_pmid` / `source_url` | MUST(至少一) | 来源标识 |

### 2.3 数据集库 `datasets.jsonl`

| 字段 | 必填 | 说明 |
|---|---|---|
| `name` | MUST | 数据集名（检索 title 等价字段来源） |
| `features` | SHOULD | 特征维度 |
| `samples` | SHOULD | 样本量 |
| `annotation` | SHOULD | 标注方式 |
| `url` | MUST | 来源 URL（本库来源标识统一用 url） |

### 2.4 证据库 `evidence.jsonl`

| 字段 | 必填 | 说明 |
|---|---|---|
| `subject` | MUST | 事实主语（title 等价字段来源） |
| `predicate` | MUST | 事实谓语/关系 |
| `object` | MUST | 事实宾语 |
| `context` | SHOULD | 上下文补充 |
| `source_pmid` / `source_doi` / `source_url` | MUST(至少一) | 来源标识 |

> 校验：灌库前运行 §8 的 `validate_records.py` 逐行检查，零错误才允许灌库。

---

## 3. 入库 payload 统一契约（灌库脚本输出）

每个分块统一输出（Chroma/Milvus 通用）：

```jsonc
{
  "id": "papers-<sha256 32位>",   // 由 collection + source_id + chunk 序号 + 文本派生，重灌稳定
  "text": "……分块正文……",         // 向量化的主体内容（documents）
  "metadata": {
    "source_id": "doi:10.xxxx/yyy",   // MUST
    "title": "……",                   // MUST，语义见 §5
    "chunk_index": 0,                 // SHOULD：分块序号（#15 起）
    "chunk_total": 5,                 // SHOULD：分块总数（#15 起）
    "chunk_start": 0, "chunk_end": 123, // SHOULD：原始文本偏移（#15 起）
    "……库专有字段……"                   // 见 §4
  }
}
```

- `id`：由 `collection`、`source_id`、`chunk_index`、分块文本计算的稳定摘要，重灌同一份数据不产生重复/漂移 ID（幂等排查用）。
- `metadata` 值 MUST 为标量（str/int/float/bool）；空值跳过；列表等复杂值先 `json.dumps` 序列化，且检索侧（如有需要）能反序列化还原。

---

## 4. metadata 专有字段标准（灌库输出）

| 库 | 必带专有字段（MUST） | 可选（SHOULD） |
|---|---|---|
| `papers` | `source_id`, `title`（论文标题） | `year`, `venue`, `authors`（逗号拼接字符串） |
| `methods` | `source_id`, `title`（= method_name）, `scenario` | `method_name`, `evaluation` |
| `datasets` | `source_id`, `title`（= name）, `name` | `features`, `samples`, `annotation` |
| `evidence` | `source_id`, `title`（= "subject predicate object"）, `subject`, `predicate`, `object` | `context` |

> ⚠️ `authors` 等数组类值在 metadata 中 MUST 预拼为字符串（如 `",".join(...)`），避免 Chroma 写库异常或检索侧解析错乱。

---

## 5. `title` 等价字段规则（本标准的修复核心）

**目标**：让 `RagSearchService` 无需改动即可检索四库（其唯一缺的就是每库 metadata 的 `title`）。

| 库 | `title` 取值规则（MUST） | 示例 |
|---|---|---|
| `papers` | 直接取 `title` | `"SSD 寿命预测综述"` |
| `methods` | `title = method_name` | `"混合效应模型"` |
| `datasets` | `title = name` | `"阿里云 SSD 公开数据集"` |
| `evidence` | `title = f"{subject} {predicate} {object}"` | `"睡眠波动 正相关于 压力评分"` |

落地位置：各 `ingest_*.py` 的 `parse_record()` 构造 metadata 处补一行即可（预计每库 1 行）。
**待办状态**：⚠️ 当前 `methods`/`datasets`/`evidence` 尚未补（关联 PR #15 正在重构灌库脚本，
由数据侧按本表补齐后同步删除本条待办）。

---

## 6. source_id 规范化（MUST）

统一输出 `doi:xxx` / `pmid:xxx` / `url:xxx`（与 ai-service `PaperEvidence.sourceId()` 对齐）：

| 输入 | 输出 |
|---|---|
| `doi` = `10.xxxx/abc` 或 `https://doi.org/10.xxxx/abc` 等 | `doi:10.xxxx/abc`（**小写**，剥离 doi.org/dx.doi.org 前缀） |
| `pmid` = `12345678` 或 `PMID: 12345678` | `pmid:12345678`（剥离 `pmid:` 前缀与空格） |
| `url` = `https://example.test/a` | `url:https://example.test/a`（不改变大小写，勿重复包 `url:` 前缀） |

- 同一篇文献的 `source_id` 在四库之间 MUST 完全一致（如论文既进论文库又出现在证据库引用中）。
- Agent 侧引用的 `evidenceIds` 即为上述规范化 `source_id`；模型输出若带前缀/URL 变体，Agent 校验前应归一化后再比对（见 §7 注）。

---

## 7. 检索侧（ai-service）字段对齐要求

`RagSearchService.search(knowledgeBase, query, topK)` 返回对象固定为 `PaperEvidence`：

| `PaperEvidence` 字段 | 来源（Chroma 响应） | 备注 |
|---|---|---|
| `title` | `metadata.title` | **MUST 非空**（本标准的修复点） |
| `content` | `documents[0][i]`（分块正文） | MUST 非空 |
| `doi` / `pmid` / `url` | 由 `metadata.source_id` 拆分 | 只允许其中一种非空 |
| `authors` | `metadata.authors`（逗号字符串） | 缺失时为空数组，合法 |
| `year` | `metadata.year` | 缺失时为 null，合法 |

> 注：Agent 做 evidenceIds 白名单比对时，若模型输出与 `source_id` 存在大小写/前缀差异，
> 应先按 §6 归一化（剥离 doi.org 前缀、转小写等）再比较，避免误判「虚构引用」。

---

## 8. 灌库前检查脚本（标准执行版）

`data/scripts/validate_records.py` 对 JSONL 输入执行 §2 字段标准的逐行校验
（解析失败、缺必填、缺来源标识、title 等价源字段为空均报错并汇总）：

```bash
# 校验单库
python scripts/validate_records.py --library papers   --input data/processed/papers.jsonl
python scripts/validate_records.py --library methods  --input data/processed/methods.jsonl
python scripts/validate_records.py --library datasets --input data/processed/datasets.jsonl
python scripts/validate_records.py --library evidence --input data/processed/evidence.jsonl

# 校验 data/processed 下全部四份（按文件名自动识别库）
python scripts/validate_records.py --input data/processed

exit code：0 = 全部通过；1 = 存在错误（逐条打印行号与原因）
```

建议：灌库流程固定为 `validate_records.py → ingest_*.py`；未来可并入 CI（data job）作自动关卡。

---

## 9. 关联与变更记录

| 日期 | 内容 | 关联 |
|---|---|---|
| 2026-09-03 | v1.0 定稿：统一四库 JSONL 输入 / payload / metadata / title 等价 / source_id 契约 | 依据 PR #15（统一分块与灌库契约）、PR #17（假设生成 Agent 审核发现 title 缺失）、docs/rag.md 第 6/7 节现有契约归纳 |
| 待办 | `ingest_methods.py` / `ingest_datasets.py` / `ingest_evidence.py` 补 §5 `title`；rag.md 第 7 节措辞与本文档对齐 | 合并 PR #15 后由数据侧完成 |
