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

## 目录结构

```
data/
├── scripts/
│   └── pdf_parser.py      # PDF 批量解析脚本（骨架）
├── raw/                   # 原始文献/数据（gitignore，不入仓库）
├── processed/             # 清洗后数据（gitignore，不入仓库）
└── datasets/              # 整理后的公开数据集（gitignore，不入仓库）
```

> 数据清洗质量直接决定 Agent 输出水平，是系统的「源头活水」（马梓涵）。
> 四库构建流程与字段约定见 [docs/rag.md](../docs/rag.md)。
