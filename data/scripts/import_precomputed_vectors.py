#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 vectors/*.vectors.jsonl 中的预计算向量幂等导入 Chroma（写入 *_vectors 集合）。

⚠️ 集合命名注意：
  本脚本写入 papers_vectors / methods_vectors / datasets_vectors / evidence_vectors（带 *_vectors 后缀）。
  若要让生产检索（RagSearchService.search）命中官方集合名（papers/methods/datasets/evidence，不带后缀），
  请改用 ingest_vectors_chroma.py。RagSearchService 现对两者都容错（哪个存在用哪个），
  但团队约定的「生产标准」是 ingest_vectors_chroma.py。

用途：既利用 4357 个论文原文分块，又不覆盖带 DOI/HTTP URL 的精选四库。
Java 检索端对两个集合做配额式混合召回。
"""

from __future__ import annotations

import argparse
import json
import math
import re
import urllib.parse
from pathlib import Path

from rag_common import get_vector_client, sanitize_metadata


LIBRARIES = ("papers", "methods", "datasets", "evidence")
EXPECTED_MODEL = "text-embedding-v4"
EXPECTED_DIMENSIONS = 1024


def local_source_id(record: dict) -> str:
    """生成可被 Java 精确反查、同时保留文件/页码/分块的稳定来源标识。"""
    library = str(record["library"]).strip()
    document_id = str(record["source_id"]).strip()
    source_file = urllib.parse.quote(str(record["source_file"]).strip(), safe="")
    page = page_number(record)
    chunk = int(record["chunk_index"])
    record_id = str(record["id"]).strip()
    page_value = str(page) if page is not None else "unknown"
    return (
        f"url:localdoc://{document_id}/{source_file}"
        f"?library={library}&page={page_value}&chunk={chunk}&id={record_id}"
    )


def display_title(record: dict) -> str:
    """本地分块的展示标题；年份仅作元数据，不从正文臆测论文题名。"""
    source_file = str(record["source_file"]).strip()
    page = page_number(record)
    chunk = int(record["chunk_index"])
    page_label = f"第{page}页" if page is not None else "页码未知"
    return f"{source_file} · {page_label} · 分块{chunk}"


def page_number(record: dict) -> int | None:
    value = record.get("page")
    if value is None or value == "":
        return None
    page = int(value)
    return page if page > 0 else None


def infer_year(source_file: str) -> int | None:
    match = re.search(r"(?:19|20)\d{2}", source_file)
    if match:
        year = int(match.group())
        if 1900 <= year <= 2100:
            return year
    return None


def validate_record(record: dict, expected_library: str, line_number: int) -> None:
    required = {
        "id", "library", "text", "embedding", "embedding_model",
        "embedding_dimensions", "source_id", "source_file", "page",
        "chunk_index", "sha256", "route_reason",
    }
    missing = sorted(required.difference(record))
    if missing:
        raise ValueError(f"第 {line_number} 行缺少字段：{', '.join(missing)}")
    if record["library"] != expected_library:
        raise ValueError(
            f"第 {line_number} 行 library={record['library']!r}，应为 {expected_library!r}"
        )
    if not str(record["id"]).strip() or not str(record["text"]).strip():
        raise ValueError(f"第 {line_number} 行 id/text 不能为空")
    if record["embedding_model"] != EXPECTED_MODEL:
        raise ValueError(
            f"第 {line_number} 行模型为 {record['embedding_model']}，应为 {EXPECTED_MODEL}"
        )
    if int(record["embedding_dimensions"]) != EXPECTED_DIMENSIONS:
        raise ValueError(
            f"第 {line_number} 行维度为 {record['embedding_dimensions']}，应为 {EXPECTED_DIMENSIONS}"
        )
    embedding = record["embedding"]
    if not isinstance(embedding, list) or len(embedding) != EXPECTED_DIMENSIONS:
        raise ValueError(f"第 {line_number} 行 embedding 不是 {EXPECTED_DIMENSIONS} 维数组")
    if not all(isinstance(value, (int, float)) and math.isfinite(value) for value in embedding):
        raise ValueError(f"第 {line_number} 行 embedding 含非数值或非有限值")


def iter_records(path: Path, library: str):
    seen_ids: set[str] = set()
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            record = json.loads(line)
            validate_record(record, library, line_number)
            record_id = str(record["id"]).strip()
            if record_id in seen_ids:
                raise ValueError(f"{path.name} 第 {line_number} 行 id 重复：{record_id}")
            seen_ids.add(record_id)
            yield record


def batches(items, size: int):
    batch = []
    for item in items:
        batch.append(item)
        if len(batch) >= size:
            yield batch
            batch = []
    if batch:
        yield batch


def metadata_of(record: dict) -> dict:
    source_file = str(record["source_file"]).strip()
    metadata = {
        "source_id": local_source_id(record),
        "document_id": str(record["source_id"]).strip(),
        "title": display_title(record),
        "source_file": source_file,
        "page": page_number(record),
        "chunk_index": int(record["chunk_index"]),
        "sha256": str(record["sha256"]).strip(),
        "route_reason": str(record["route_reason"]).strip(),
        "library": str(record["library"]).strip(),
        "embedding_model": str(record["embedding_model"]).strip(),
        "embedding_dimensions": int(record["embedding_dimensions"]),
        "year": infer_year(source_file),
        "authors": "",
    }
    return sanitize_metadata(metadata)


def import_library(client, vectors_dir: Path, library: str, batch_size: int) -> int:
    path = vectors_dir / f"{library}.vectors.jsonl"
    if not path.exists():
        raise FileNotFoundError(f"缺少向量文件：{path}")
    collection = client.get_or_create_collection(
        name=f"{library}_vectors",
        metadata={
            "hnsw:space": "cosine",
            "embedding_model": EXPECTED_MODEL,
            "embedding_dimensions": EXPECTED_DIMENSIONS,
            "source": "precomputed-jsonl",
        },
    )
    total = 0
    for batch in batches(iter_records(path, library), batch_size):
        collection.upsert(
            ids=[str(item["id"]) for item in batch],
            embeddings=[item["embedding"] for item in batch],
            documents=[str(item["text"]).strip() for item in batch],
            metadatas=[metadata_of(item) for item in batch],
        )
        total += len(batch)
        print(f"  {library}_vectors: {total} 条", flush=True)
    return total


def main() -> None:
    parser = argparse.ArgumentParser(description="导入预计算四库向量到 Chroma")
    parser.add_argument("--input", default="vectors", help="*.vectors.jsonl 所在目录")
    parser.add_argument("--batch-size", type=int, default=64, help="单次写入条数")
    parser.add_argument(
        "--check-only", action="store_true", help="只校验文件，不连接或写入 Chroma"
    )
    args = parser.parse_args()
    if args.batch_size <= 0:
        raise SystemExit("--batch-size 必须大于 0")

    vectors_dir = Path(args.input).resolve()
    print(f"向量目录：{vectors_dir}")
    if args.check_only:
        for library in LIBRARIES:
            path = vectors_dir / f"{library}.vectors.jsonl"
            count = sum(1 for _ in iter_records(path, library))
            print(f"✅ {library}: {count} 条，{EXPECTED_MODEL}/{EXPECTED_DIMENSIONS}维")
        return

    client = get_vector_client()
    counts = {
        library: import_library(client, vectors_dir, library, args.batch_size)
        for library in LIBRARIES
    }
    print("✅ 预计算向量导入完成：" + ", ".join(
        f"{library}={count}" for library, count in counts.items()
    ))


if __name__ == "__main__":
    main()
