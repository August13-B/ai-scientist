#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
证据库灌库脚本（collection: evidence）。

输入 JSONL（data/processed/evidence.jsonl），每行一条科学事实三元组：
    {"subject": "A", "predicate": "导致", "object": "B",
     "source_pmid": "12345678", "context": "原文上下文（可选）"}

字段约定：source_pmid / source_doi / source_url 至少一种，作为来源标识。

用法：
    python scripts/ingest_evidence.py --input data/processed/evidence.jsonl
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rag_base import BaseIngester, main  # noqa: E402


class EvidenceIngester(BaseIngester):

    COLLECTION_NAME = "evidence"
    CHUNK_SIZE = 512
    CHUNK_OVERLAP = 64

    def parse_record(self, record: dict) -> list[dict]:
        subject = record.get("subject") or ""
        predicate = record.get("predicate") or ""
        obj = record.get("object") or ""
        context = record.get("context") or ""

        source_id = normalize_source(
            pmid=record.get("source_pmid"), doi=record.get("source_doi"), url=record.get("source_url")
        )
        metadata = {
            "source_id": source_id,
            "subject": subject,
            "predicate": predicate,
            "object": obj,
        }

        # 证据为短三元组，整条一个 chunk
        text = f"{subject} {predicate} {obj}。{context}".strip()
        chunks = self.split_text(text, self.CHUNK_SIZE, self.CHUNK_OVERLAP)
        return [{"text": c, "metadata": dict(metadata)} for c in chunks]


def normalize_source(pmid=None, doi=None, url=None) -> str:
    for value, prefix in ((pmid, "pmid:"), (doi, "doi:"), (url, "url:")):
        if value and str(value).strip():
            return f"{prefix}{str(value).strip()}"
    raise ValueError("每条证据必须包含 source_pmid / source_doi / source_url 至少一种")


if __name__ == "__main__":
    main(EvidenceIngester())
