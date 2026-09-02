#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
方法库灌库脚本（collection: methods）。

输入 JSONL（data/processed/methods.jsonl），每行一条方法条目：
    {"method_name": "CNN", "scenario": "图像分类", "steps": ["..."],
     "evaluation": "精度 95%", "source_doi": "10.xxxx/..."}

字段约定：source_doi / source_pmid / source_url 至少一种，作为来源标识。

用法：
    python scripts/ingest_methods.py --input data/processed/methods.jsonl
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rag_base import BaseIngester, main  # noqa: E402
from rag_common import normalize_source  # noqa: E402


class MethodsIngester(BaseIngester):

    COLLECTION_NAME = "methods"
    CHUNK_SIZE = 512
    CHUNK_OVERLAP = 64

    def parse_record(self, record: dict) -> list[dict]:
        name = record.get("method_name") or "未命名方法"
        scenario = record.get("scenario") or ""
        steps = "；".join(record.get("steps") or [])
        evaluation = record.get("evaluation") or ""

        source_id = normalize_source(
            doi=record.get("source_doi"), pmid=record.get("source_pmid"), url=record.get("source_url")
        )
        metadata = {
            "source_id": source_id,
            "method_name": name,
            "scenario": scenario,
            "evaluation": evaluation,
        }

        # 方法条目为结构化短文本，整条作为一个 chunk（不超过 chunk_size 时不拆分）
        text = f"方法：{name}\n适用场景：{scenario}\n实施步骤：{steps}\n评估结果：{evaluation}"
        return self.create_chunk_payloads(text, metadata)


if __name__ == "__main__":
    main(MethodsIngester())
