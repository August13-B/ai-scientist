#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
读取马艺萌已向量化的四库 JSONL（output/vectors/*.vectors.jsonl），
用现成的 embedding（text-embedding-v4，1024 维）直接灌入 Chroma——不重复调用向量模型。

用途：生产环境 RAG 调用。马艺萌那边已算好向量，本脚本只做「搬运入库」，
并把 metadata 对齐 ai-service 的 RagSearchService/PaperEvidence 契约：
  - source_id：从 text 提取真实 DOI/PMID/URL → doi:xxx（供引用核验）；提取不到 → url:doc-<sha256>
  - title / content / source_file / page / sha256 / chunk_index / route_reason 溯源字段

用法（在 data/ 目录下）：
    python scripts/ingest_vectors_chroma.py --vectors /path/to/output/vectors
    python scripts/ingest_vectors_chroma.py --vectors ../马艺萌-四库数据与向量化-最新版/output/vectors

四库 collection 名：papers / methods / datasets / evidence（与 RagSearchService 一致）。
"""

import argparse
import json
import os
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rag_common import get_vector_client, sanitize_metadata, vector_db  # noqa: E402


# ==================== source_id / title 提取 ====================

# 各类来源标识的正则
_DOI = re.compile(r"10\.\d{4,9}/[-._;()/:a-zA-Z0-9]+")
_URL = re.compile(r"https?://[^\s]+")
_PMID = re.compile(r"(?i)\bpmid[:#\s]*(\d{6,9})")
# 论文名（从 PDF/DOCX 文件名，去扩展名与年份前缀）
_TITLE_FROM_FILE = re.compile(r"(?P<title>[A-Za-z0-9][A-Za-z0-9\s\-_]{3,})\.(pdf|docx|PDF|DOCX)$")


def extract_source_id(text: str, sha256: str) -> str:
    """优先提取真实 DOI / PMID / URL；提取不到用 url:doc-<sha256> 兜底（可溯源）。"""
    doi = _DOI.search(text or "")
    if doi:
        return "doi:" + doi.group(0).rstrip(".,;)")
    pmid = _PMID.search(text or "")
    if pmid:
        return "pmid:" + pmid.group(1)
    url = _URL.search(text or "")
    if url:
        return "url:" + url.group(0).rstrip(".,;)")
    return "url:doc-" + (sha256 or "")


def extract_title(record: dict) -> str:
    """优先从 source_file 文件名提取论文名；否则用 text 首行（截断）。"""
    source_file = record.get("source_file") or ""
    match = _TITLE_FROM_FILE.search(source_file)
    if match:
        title = match.group("title").strip()
        if len(title) > 3:
            return title
    text = (record.get("text") or "").strip()
    if text:
        first_line = text.split("\n")[0].strip()
        return first_line[:120] if first_line else "无标题"
    return "无标题"


# ==================== 主流程 ====================

def ingest_one(client, collection_name: str, records: list[dict]) -> None:
    ids, documents, metadatas, embeddings = [], [], [], []
    skipped = 0
    for rec in records:
        embedding = rec.get("embedding")
        text = rec.get("text") or ""
        if not embedding or not text:
            skipped += 1
            continue
        sha = rec.get("sha256") or ""
        source_id = extract_source_id(text, sha)
        title = extract_title(rec)
        metadata = sanitize_metadata({
            "source_id": source_id,
            "title": title,
            "content": text,
            "source_file": rec.get("source_file") or "",
            "page": rec.get("page"),
            "sha256": sha,
            "chunk_index": rec.get("chunk_index"),
            "route_reason": rec.get("route_reason") or "",
        })
        ids.append(f"{collection_name}-{sha[:16]}-{rec.get('chunk_index', 0)}")
        documents.append(text)
        metadatas.append(metadata)
        embeddings.append(embedding)

    if not ids:
        print(f"  ⚠ {collection_name}: 无有效向量记录")
        return

    dim = len(embeddings[0])
    expected = int(os.getenv("EMBEDDING_DIMENSIONS", "1024"))
    if dim != expected:
        print(f"  ❌ {collection_name}: 向量维度 {dim} 与 EMBEDDING_DIMENSIONS={expected} 不一致")
        sys.exit(1)

    try:
        client.delete_collection(collection_name)  # 幂等：重灌先删
    except Exception:
        pass
    collection = client.get_or_create_collection(
        collection_name, metadata={"hnsw:space": "cosine"})
    collection.add(ids=ids, embeddings=embeddings, documents=documents, metadatas=metadatas)
    dims = set(len(e) for e in embeddings)
    if len(dims) > 1:
        print(f"  ❌ {collection_name}: 向量维度不一致 {dims}")
        sys.exit(1)
    print(f"  ✅ {collection_name}: 灌入 {len(ids)} 条向量（dim={dim}，来源 {len(set(m['source_id'] for m in metadatas))}）")


def main() -> None:
    parser = argparse.ArgumentParser(description="把马艺萌已向量化 JSONL 导入 Chroma（不重复向量化）")
    parser.add_argument("--vectors", required=True,
                        help="马艺萌 output/vectors 目录（含 papers/methods/datasets/evidence.vectors.jsonl）")
    args = parser.parse_args()

    vectors_dir = Path(args.vectors)
    if not vectors_dir.exists():
        print(f"❌ 目录不存在: {vectors_dir}")
        sys.exit(1)

    client = get_vector_client()
    print(f"[chroma] 客户端 = {vector_db()}, 向量目录 = {vectors_dir}")

    for name in ("papers", "methods", "datasets", "evidence"):
        path = vectors_dir / f"{name}.vectors.jsonl"
        if not path.exists():
            print(f"  ⚠ 缺失 {path}，跳过")
            continue
        records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines()
                   if line.strip()]
        print(f"  [load] {name}.vectors.jsonl: {len(records)} 条")
        ingest_one(client, name, records)

    print("✅ 全部导入完成")


if __name__ == "__main__":
    main()
