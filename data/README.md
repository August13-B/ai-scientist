# data — 数据处理引擎

Python 脚本：批量解析学术 PDF、数据清洗、构建四库 RAG 的灌库数据。

## 职责

- 批量解析目标领域 PDF 论文（pdfplumber + Grobid），提取标题/摘要/章节/参考文献
- 多模态实测数据（实验记录、观测数据）结构化处理
- 产出四份**合规真实**的高质量数据集：论文库/方法库/数据集库/证据库
- 供 ai-service 灌入向量数据库（Chroma/Milvus）

## 快速开始

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/pdf_parser.py --input ./pdfs --output ./processed
```

## 四库灌库（RAG）

> 🚨 **字段标准**：灌库前必读 **[docs/rag-field-standard.md](../docs/rag-field-standard.md)**（JSONL 输入契约 / 入库 metadata / title 等价字段 / source_id 规则）；标准执行版校验脚本见下方 `validate_records.py`。

数据清洗产物为 JSONL（每行一条，必带来源标识），灌库脚本写入 Chroma（开发）/ Milvus（生产）：

```bash
# 1. 启动向量库（开发用 Chroma）
docker compose up -d chroma

# 2. 配置 .env（ALIYUN_BAILIAN_API_KEY 必填，供 Embedding 用）
cp ../.env.example ../.env

# 3. 灌库前校验（四库逐份通过后再灌，exit code 0 才允许继续）
python scripts/validate_records.py --input data/processed

# 4. 四库分别灌入
python scripts/ingest_papers.py --input data/processed/papers.jsonl
python scripts/ingest_methods.py --input data/processed/methods.jsonl
python scripts/ingest_datasets.py --input data/processed/datasets.jsonl
python scripts/ingest_evidence.py --input data/processed/evidence.jsonl
```

- 向量化：百炼 DashScope Embedding API（`EMBEDDING_PROVIDER=dashscope`，模型 text-embedding-v3）
- 目标库切换：`.env` 的 `VECTOR_DB=chroma|milvus`
- 分块：默认 `chunk_size=512, overlap=64`（脚本 `--chunk-size/--chunk-overlap` 可覆盖），优先按段落、换行和中英文句末切分；仅超长语义单元才按字符截断。
- 四库统一写入 payload：`id`、`text`、`metadata`。metadata 固定含 `source_id`、`chunk_index`、`chunk_total`、`chunk_start`、`chunk_end`；ID 可重现，方便增量排查和重灌。
- 幂等：重灌先删后建；每条记录必须携带 `doi/pmid/url` 来源标识（幻觉检测地基）

## 目录结构

```
data/
├── scripts/
│   ├── pdf_parser.py       # PDF 批量解析脚本（骨架）
│   ├── rag_common.py       # 公共配置：向量库连接 + Embedding（百炼）
│   ├── chunking.py         # 语义优先分块器（段落/句子边界 + 偏移）
│   ├── rag_base.py         # 灌库公共基类（统一 chunk 契约 → 向量化 → 入库）
│   ├── validate_records.py # 灌库输入 JSONL 契约校验（标准：docs/rag-field-standard.md）
│   ├── ingest_papers.py    # 论文库灌库（collection: papers）
│   ├── ingest_methods.py   # 方法库灌库（collection: methods）
│   ├── ingest_datasets.py  # 数据集库灌库（collection: datasets）
│   └── ingest_evidence.py  # 证据库灌库（collection: evidence）
├── tests/
│   └── test_chunking.py    # 分块与四库输出契约单元测试
├── raw/                   # 原始文献/数据（gitignore，不入仓库）
├── processed/             # 清洗后数据（gitignore，不入仓库）
└── datasets/              # 整理后的公开数据集（gitignore，不入仓库）
```

> 数据清洗质量直接决定 Agent 输出水平，是系统的「源头活水」（马梓涵）。
> 四库构建流程与字段约定见 [docs/rag.md](../docs/rag.md)。
