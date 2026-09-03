#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
数据集库灌库脚本（collection: datasets）。

输入 JSONL（data/processed/datasets.jsonl），每行一条数据集元信息：
    {"name": "Alibaba SSD", "features": 128, "samples": 1000000,
     "annotation": "标签说明", "url": "https://..."}

字段约定：url（或 doi）必填，作为来源标识。

用法：
    python scripts/ingest_datasets.py --input data/processed/datasets.jsonl
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rag_base import BaseIngester, main  # noqa: E402
from rag_common import normalize_source  # noqa: E402


class DatasetsIngester(BaseIngester):

    COLLECTION_NAME = "datasets"
    CHUNK_SIZE = 512
    CHUNK_OVERLAP = 64

    def parse_record(self, record: dict) -> list[dict]:
        name = record.get("name") or "未命名数据集"
        features = record.get("features")
        samples = record.get("samples")
        annotation = record.get("annotation") or ""
        url = record.get("url")

        if not url:
            raise ValueError(f"数据集 [{name}] 缺少 url 来源标识")

        metadata = {
            "source_id": f"url:{url.strip()}",
            "title": name,       # 检索侧 PaperEvidence 需要（docs/rag-field-standard.md §5）
            "name": name,
            "features": features,
            "samples": samples,
            "annotation": annotation,
        }

        text = (
            f"数据集：{name}\n特征维度：{features}\n样本量：{samples}\n"
            f"标注方式：{annotation}\n来源：{url}"
        )
        return self.create_chunk_payloads(text, metadata)


if __name__ == "__main__":
    main(DatasetsIngester())
