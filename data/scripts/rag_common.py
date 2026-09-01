#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
公共配置：向量库连接与 Embedding 配置（从 .env 读取）。

用法：
    from config import get_vector_client, embed_texts
"""

import os

try:
    from dotenv import load_dotenv
    load_dotenv()  # 读取仓库根目录 .env（不进 git）
except ImportError:
    pass


# ==================== 向量库连接 ====================

def vector_db() -> str:
    """当前向量库类型：chroma | milvus（.env VECTOR_DB，默认 chroma）"""
    return os.getenv("VECTOR_DB", "chroma").strip().lower()


def get_vector_client():
    """按 VECTOR_DB 返回向量库客户端（Chroma / Milvus 双支持）。"""
    db = vector_db()
    if db == "milvus":
        return _get_milvus_client()
    return _get_chroma_client()


def _get_chroma_client():
    import chromadb
    from chromadb.config import Settings

    host = os.getenv("CHROMA_HOST", "localhost")
    port = int(os.getenv("CHROMA_PORT", "8000"))
    # Chroma HTTP 客户端（连接 docker-compose 起的 chroma 容器）
    return chromadb.HttpClient(host=host, port=port, settings=Settings(anonymized_telemetry=False))


def _get_milvus_client():
    from pymilvus import MilvusClient

    host = os.getenv("MILVUS_HOST", "localhost")
    port = os.getenv("MILVUS_PORT", "19530")
    return MilvusClient(uri=f"http://{host}:{port}")


# ==================== Embedding（百炼 DashScope API）====================

def embedding_provider() -> str:
    """Embedding 提供方：dashscope（默认）"""
    return os.getenv("EMBEDDING_PROVIDER", "dashscope").strip().lower()


def embed_texts(texts: list[str], batch_size: int = 25) -> list[list[float]]:
    """
    批量向量化文本（百炼 DashScope Embedding API）。

    :param texts: 文本列表（如分块后的 chunk）
    :param batch_size: 百炼单次请求上限（默认 25）
    :return: 向量列表，与输入顺序一致
    """
    provider = embedding_provider()
    if provider == "dashscope":
        return _embed_dashscope(texts, batch_size)
    raise ValueError(f"不支持的 EMBEDDING_PROVIDER: {provider}")


def _embed_dashscope(texts: list[str], batch_size: int) -> list[list[float]]:
    import dashscope
    from dashscope import TextEmbedding

    api_key = os.getenv("ALIYUN_BAILIAN_API_KEY", "")
    if not api_key:
        raise RuntimeError("未配置 ALIYUN_BAILIAN_API_KEY（写入 .env）")

    model = os.getenv("EMBEDDING_MODEL", "text-embedding-v3")
    dashscope.api_key = api_key

    vectors: list[list[float]] = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]
        resp = TextEmbedding.call(model=model, input=batch)
        if resp.status_code != 200:
            raise RuntimeError(f"Embedding 调用失败: {resp.status_code} {resp.message}")
        vectors.extend(item["embedding"] for item in resp.output["embeddings"])
    return vectors
