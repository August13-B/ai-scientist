#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
论文库灌库脚本（collection: papers）。

输入 JSONL（data/processed/papers.jsonl），每行一条论文：
    {"title": "...", "abstract": "...", "content": "...",
     "authors": ["..."], "year": 2025, "venue": "...", "doi": "10.xxxx/..."}

字段约定：doi 必填（无 doi 时可用 pmid/url），作为来源标识支撑幻觉检测。

用法：
    python scripts/ingest_papers.py --input data/processed/papers.jsonl
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rag_base import BaseIngester, main  # noqa: E402


class PapersIngester(BaseIngester):

    COLLECTION_NAME = "papers"
    CHUNK_SIZE = 512
    CHUNK_OVERLAP = 64

    def parse_record(self, record: dict) -> list[dict]:
        title = record.get("title") or "无标题"
        abstract = record.get("abstract") or ""
        content = record.get("content") or ""
        source_id = normalize_source(
            doi=record.get("doi"), pmid=record.get("pmid"), url=record.get("url")
        )

        # 元数据（Chroma/Milvus 通用字段；来源标识必带）
        metadata = {
            "source_id": source_id,
            "title": title,
            "year": record.get("year"),
            "venue": record.get("venue"),
            "authors": ",".join(record.get("authors") or []),
        }

        # 分块对象：摘要+正文拼接后按 512/64 分块
        text = f"{title}\n{abstract}\n{content}".strip()
        chunks = self.split_text(text, self.CHUNK_SIZE, self.CHUNK_OVERLAP)
        return [{"text": c, "metadata": dict(metadata)} for c in chunks]


def normalize_source(doi=None, pmid=None, url=None) -> str:
    """规范化来源标识（与 ai-service PaperEvidence.sourceId 对齐）：doi:xxx / pmid:xxx / url:xxx。"""
    if doi:
        d = doi.strip()
        for prefix in ("doi:", "http://dx.doi.org/", "https://dx.doi.org/", "http://doi.org/", "https://doi.org/"):
            if d.lower().startswith(prefix):
                d = d[len(prefix):]
        return f"doi:{d.lower()}"
    if pmid:
        return f"pmid:{pmid.strip().lstrip('pmid:').lstrip('PMID:')}"
    if url:
        return f"url:{url.strip()}"
    raise ValueError("每条论文必须包含 doi / pmid / url 至少一种来源标识")


if __name__ == "__main__":
    main(PapersIngester())
