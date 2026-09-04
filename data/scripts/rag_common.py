#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
公共配置：向量库连接与 Embedding 配置（从 .env 读取）。

用法：
    from config import get_vector_client, embed_texts
"""

import os
import json
import urllib.error
import urllib.parse
import urllib.request

try:
    from dotenv import load_dotenv
    load_dotenv()  # 读取仓库根目录 .env（不进 git）
except ImportError:
    pass


def normalize_source(doi=None, pmid=None, url=None) -> str:
    """规范来源 ID，供四库写入与检索侧引用核验共同使用。"""
    if doi and str(doi).strip():
        value = str(doi).strip()
        prefixes = (
            "doi:", "https://doi.org/", "http://doi.org/",
            "https://dx.doi.org/", "http://dx.doi.org/",
        )
        for prefix in prefixes:
            if value.lower().startswith(prefix):
                value = value[len(prefix):]
                break
        return f"doi:{value.lower()}"
    if pmid and str(pmid).strip():
        value = str(pmid).strip()
        if value.lower().startswith("pmid:"):
            value = value[5:].strip()
        return f"pmid:{value}"
    if url and str(url).strip():
        return f"url:{str(url).strip()}"
    raise ValueError("每条记录必须包含 doi / pmid / url 至少一种来源标识")


def sanitize_metadata(metadata: dict) -> dict:
    """移除空值并转换为 Chroma 与 Milvus 都可接受的元数据类型。"""
    cleaned = {}
    for key, value in metadata.items():
        if value is None:
            continue
        if isinstance(value, (str, int, float, bool)):
            cleaned[key] = value
        else:
            cleaned[key] = json.dumps(value, ensure_ascii=False, sort_keys=True)
    return cleaned


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
    host = os.getenv("CHROMA_HOST", "localhost")
    port = int(os.getenv("CHROMA_PORT", "8000"))
    # Chroma 1.0 的服务端仅开放 /api/v2；使用标准库 HTTP 客户端可避免
    # Windows/Python 3.12 环境安装 chroma-hnswlib 时依赖本地 C++ 编译器。
    return ChromaV2HttpClient(host=host, port=port)


class ChromaV2HttpClient:
    """满足灌库脚本所需最小接口的 Chroma v2 HTTP 客户端。"""

    COLLECTIONS_PATH = (
        "/api/v2/tenants/default_tenant/databases/default_database/collections"
    )

    def __init__(self, host: str, port: int, timeout: int = 30):
        self.base_url = f"http://{host}:{port}"
        self.timeout = timeout

    def _request(self, method: str, path: str, payload=None):
        data = None
        headers = {}
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"
        request = urllib.request.Request(
            self.base_url + path, data=data, headers=headers, method=method
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = response.read().decode("utf-8")
                return json.loads(body) if body else None
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(
                f"Chroma v2 请求失败 HTTP {error.code}: {body[:300]}"
            ) from error
        except urllib.error.URLError as error:
            raise RuntimeError(f"无法连接 Chroma: {error.reason}") from error

    def _find_collection(self, name: str):
        collections = self._request("GET", self.COLLECTIONS_PATH) or []
        return next((item for item in collections if item.get("name") == name), None)

    def delete_collection(self, name: str):
        collection = self._find_collection(name)
        if collection is None:
            raise ValueError(f"collection not found: {name}")
        collection_id = urllib.parse.quote(collection["id"], safe="")
        return self._request("DELETE", f"{self.COLLECTIONS_PATH}/{collection_id}")

    def get_or_create_collection(self, name: str, metadata=None):
        collection = self._find_collection(name)
        if collection is None:
            payload = {"name": name}
            if metadata:
                payload["metadata"] = metadata
            collection = self._request("POST", self.COLLECTIONS_PATH, payload)
        return ChromaV2Collection(self, collection["id"], name)

    def get_collection(self, name: str):
        collection = self._find_collection(name)
        if collection is None:
            raise ValueError(f"collection not found: {name}")
        return ChromaV2Collection(self, collection["id"], name)


class ChromaV2Collection:
    """Chroma v2 collection 的最小写入适配器。"""

    def __init__(self, client: ChromaV2HttpClient, collection_id: str, name: str):
        self.client = client
        self.id = collection_id
        self.name = name

    def add(self, ids, embeddings, documents, metadatas):
        collection_id = urllib.parse.quote(self.id, safe="")
        return self.client._request(
            "POST",
            f"{self.client.COLLECTIONS_PATH}/{collection_id}/add",
            {
                "ids": ids,
                "embeddings": embeddings,
                "documents": documents,
                "metadatas": metadatas,
            },
        )

    def upsert(self, ids, embeddings, documents, metadatas):
        """按 id 幂等写入，适合导入已计算且支持断点重跑的向量文件。"""
        collection_id = urllib.parse.quote(self.id, safe="")
        return self.client._request(
            "POST",
            f"{self.client.COLLECTIONS_PATH}/{collection_id}/upsert",
            {
                "ids": ids,
                "embeddings": embeddings,
                "documents": documents,
                "metadatas": metadatas,
            },
        )


def _get_milvus_client():
    from pymilvus import MilvusClient

    host = os.getenv("MILVUS_HOST", "localhost")
    port = os.getenv("MILVUS_PORT", "19530")
    return MilvusClient(uri=f"http://{host}:{port}")


# ==================== Embedding（百炼 DashScope API）====================

def embedding_provider() -> str:
    """Embedding 提供方：dashscope（默认）"""
    return os.getenv("EMBEDDING_PROVIDER", "dashscope").strip().lower()


def embed_texts(texts: list[str], batch_size: int = 10) -> list[list[float]]:
    """
    批量向量化文本（百炼 DashScope Embedding API）。

    :param texts: 文本列表（如分块后的 chunk）
    :param batch_size: text-embedding-v4 单次请求上限（默认 10）
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

    model = os.getenv("EMBEDDING_MODEL", "text-embedding-v4")
    dashscope.api_key = api_key

    vectors: list[list[float]] = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]
        resp = TextEmbedding.call(model=model, input=batch)
        if resp.status_code != 200:
            raise RuntimeError(f"Embedding 调用失败: {resp.status_code} {resp.message}")
        vectors.extend(item["embedding"] for item in resp.output["embeddings"])
    return vectors
