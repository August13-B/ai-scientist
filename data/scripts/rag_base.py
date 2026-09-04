#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
四库灌库公共基类。

用法（子类实现 parse_record 与 collection 名）：
    python scripts/ingest_papers.py --input data/processed/papers.jsonl
"""

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path

# 允许从 data/scripts 直接运行
sys.path.insert(0, str(Path(__file__).resolve().parent))
from chunking import split_text  # noqa: E402
from rag_common import embed_texts, get_vector_client, sanitize_metadata  # noqa: E402


class BaseIngester:
    """灌库基类：读取 JSONL → 分块 → 向量化 → 写入向量库（幂等：先删后建）。"""

    # 子类覆盖
    COLLECTION_NAME = "default"
    CHUNK_SIZE = 512
    CHUNK_OVERLAP = 64

    def parse_record(self, record: dict) -> list[dict]:
        """
        子类实现：把一条 JSONL 记录拆成「可向量化的文本块 + 元数据」。
        返回统一 chunk payload：{"id": "...", "text": "...", "metadata": {...}}。
        metadata 每条必须带 source_id（来源标识，支撑幻觉检测）。
        """
        raise NotImplementedError

    def create_chunk_payloads(self, text: str, metadata: dict) -> list[dict]:
        """生成四库统一的、包含稳定 ID 与位置元数据的 chunk 契约。"""
        source_id = metadata.get("source_id")
        if not source_id:
            raise ValueError("chunk metadata 必须包含 source_id")
        parts = split_text(text, self.CHUNK_SIZE, self.CHUNK_OVERLAP)
        total = len(parts)
        collection = getattr(self, "_active_collection", self.COLLECTION_NAME)
        payloads = []
        for index, part in enumerate(parts):
            digest = hashlib.sha256(
                f"{collection}\x1f{source_id}\x1f{index}\x1f{part.text}".encode("utf-8")
            ).hexdigest()[:32]
            payload_metadata = sanitize_metadata({
                **metadata,
                "chunk_index": index,
                "chunk_total": total,
                "chunk_start": part.start,
                "chunk_end": part.end,
            })
            payloads.append({"id": f"{collection}-{digest}", "text": part.text, "metadata": payload_metadata})
        return payloads

    # ==================== 主流程 ====================

    def run(self, input_path: str, collection: str | None) -> None:
        records = self._load_jsonl(input_path)
        if not records:
            print(f"❌ 无有效记录: {input_path}")
            sys.exit(1)

        collection_name = collection or self.COLLECTION_NAME
        self._active_collection = collection_name
        chunks = []
        for record in records:
            chunks.extend(self.parse_record(record))
        print(f"  [解析] {len(records)} 条记录 → {len(chunks)} 个分块")

        client = get_vector_client()
        self._ensure_collection(client, collection_name)

        ids, documents, metadatas, embeddings = [], [], [], []
        for chunk in chunks:
            ids.append(chunk["id"])
            documents.append(chunk["text"])
            metadatas.append(chunk["metadata"])
        embeddings = embed_texts(documents)

        self._add(client, collection_name, ids, documents, metadatas, embeddings)
        print(f"✅ 灌库完成: {collection_name} ← {len(chunks)} 条向量")

    # ==================== 向量库写入（Chroma / Milvus 双支持）====================

    def _ensure_collection(self, client, name: str) -> None:
        if vector_db_type() == "milvus":
            if client.has_collection(name):
                client.drop_collection(name)  # 幂等：重灌先删
            client.create_collection(name, dimension=self._dimension())
        else:
            try:
                client.delete_collection(name)  # 幂等：重灌先删
            except Exception:
                pass
            client.get_or_create_collection(name, metadata={"hnsw:space": "cosine"})

    def _dimension(self) -> int:
        # text-embedding-v3 默认 1024 维；如需其他维度在 .env EMBEDDING_DIMENSIONS 覆盖
        return int(os.getenv("EMBEDDING_DIMENSIONS", "1024"))

    def _add(self, client, name, ids, documents, metadatas, embeddings) -> None:
        if vector_db_type() == "milvus":
            client.insert(
                collection_name=name,
                data=[
                    {
                        # Milvus 默认主键为整数；从统一 chunk ID 派生以保持重灌稳定。
                        "id": int(hashlib.sha256(chunk_id.encode("utf-8")).hexdigest()[:15], 16),
                        "vector": emb,
                        "text": doc,
                        **meta,
                    }
                    for chunk_id, doc, meta, emb in zip(ids, documents, metadatas, embeddings)
                ],
            )
        else:
            client.get_collection(name).add(
                ids=ids, embeddings=embeddings, documents=documents, metadatas=metadatas
            )

    # ==================== 工具 ====================

    @staticmethod
    def _load_jsonl(path: str) -> list[dict]:
        p = Path(path)
        if not p.exists():
            print(f"❌ 文件不存在: {path}")
            sys.exit(1)
        records = []
        with p.open("r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    records.append(json.loads(line))
                except json.JSONDecodeError as e:
                    print(f"  ⚠️ 跳过坏行: {e}")
        return records

def vector_db_type() -> str:
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from rag_common import vector_db  # noqa: E402

    return vector_db()


def main(ingester: BaseIngester) -> None:
    parser = argparse.ArgumentParser(description="RAG 知识库灌库脚本")
    parser.add_argument("--input", required=True, help="JSONL 输入文件（data/processed/xxx.jsonl）")
    parser.add_argument("--collection", default=None, help="覆盖默认 collection 名")
    parser.add_argument("--chunk-size", type=int, default=ingester.CHUNK_SIZE, help="分块大小")
    parser.add_argument("--chunk-overlap", type=int, default=ingester.CHUNK_OVERLAP, help="分块重叠")
    args = parser.parse_args()

    ingester.CHUNK_SIZE = args.chunk_size
    ingester.CHUNK_OVERLAP = args.chunk_overlap
    ingester.run(args.input, args.collection)
