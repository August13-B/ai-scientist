#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""向量分块与四库输出契约的无外部依赖测试。"""

import sys
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPTS_DIR))

from chunking import split_text
from ingest_datasets import DatasetsIngester
from ingest_evidence import EvidenceIngester
from ingest_methods import MethodsIngester
from ingest_papers import PapersIngester
from rag_common import normalize_source, sanitize_metadata


class ChunkingTest(unittest.TestCase):
    def test_prefers_sentence_boundary(self):
        text = "第一句内容。第二句内容。第三句内容。"
        chunks = split_text(text, chunk_size=12, overlap=0)
        self.assertEqual([chunk.text for chunk in chunks], ["第一句内容。第二句内容。", "第三句内容。"])

    def test_chunk_length_and_offsets(self):
        text = "段落一有足够长的内容用于测试切分。\n\n段落二也有足够长的内容用于测试切分。"
        chunks = split_text(text, chunk_size=18, overlap=4)
        self.assertTrue(chunks)
        self.assertTrue(all(len(chunk.text) <= 18 for chunk in chunks))
        self.assertTrue(all(text[chunk.start:chunk.end] == chunk.text for chunk in chunks))

    def test_rejects_invalid_overlap(self):
        with self.assertRaises(ValueError):
            split_text("文本", chunk_size=10, overlap=10)
        with self.assertRaises(ValueError):
            split_text("文本", chunk_size=0, overlap=0)


class CommonContractTest(unittest.TestCase):
    def test_normalize_source(self):
        self.assertEqual(normalize_source(doi="https://doi.org/10.1000/ABC"), "doi:10.1000/abc")
        self.assertEqual(normalize_source(pmid="PMID: 12345"), "pmid:12345")
        self.assertEqual(normalize_source(url=" https://example.test/a "), "url:https://example.test/a")

    def test_sanitize_metadata(self):
        metadata = sanitize_metadata({"title": "x", "year": None, "authors": ["a", "b"]})
        self.assertEqual(metadata, {"title": "x", "authors": '["a", "b"]'})

    def test_all_ingesters_emit_the_same_chunk_contract(self):
        examples = [
            (PapersIngester(), {"title": "论文", "abstract": "摘要。", "content": "正文。", "doi": "10.1/a"}),
            (MethodsIngester(), {"method_name": "方法", "scenario": "场景", "steps": ["步骤"], "evaluation": "结果", "source_doi": "10.1/b"}),
            (DatasetsIngester(), {"name": "数据集", "features": 3, "samples": 5, "annotation": "标注", "url": "https://example.test/dataset"}),
            (EvidenceIngester(), {"subject": "A", "predicate": "影响", "object": "B", "context": "上下文", "source_pmid": "123"}),
        ]
        for ingester, record in examples:
            first = ingester.parse_record(record)
            second = ingester.parse_record(record)
            self.assertEqual(first[0]["id"], second[0]["id"])
            self.assertEqual(set(first[0]), {"id", "text", "metadata"})
            metadata = first[0]["metadata"]
            self.assertTrue(metadata["source_id"])
            # 检索侧 PaperEvidence 强制 title 非空（docs/rag-field-standard.md §5）
            self.assertTrue(metadata.get("title"), f"metadata 缺少 title（{record}）")
            self.assertEqual(metadata["chunk_index"], 0)
            self.assertEqual(metadata["chunk_total"], len(first))
            self.assertIn("chunk_start", metadata)
            self.assertIn("chunk_end", metadata)

    def test_collection_override_changes_stable_id_namespace(self):
        ingester = PapersIngester()
        record = {"title": "论文", "abstract": "摘要", "content": "正文", "doi": "10.1/a"}
        default_id = ingester.parse_record(record)[0]["id"]
        ingester._active_collection = "papers-preview"
        overridden_id = ingester.parse_record(record)[0]["id"]
        self.assertNotEqual(default_id, overridden_id)
        self.assertTrue(overridden_id.startswith("papers-preview-"))


if __name__ == "__main__":
    unittest.main()
