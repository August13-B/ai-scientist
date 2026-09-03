#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Chroma v2 探针（纯 HTTP，零依赖——仅标准库）。

用 urllib 直接调 /api/v2 端点，验证 RagSearchService（Java）与 ingest_vectors_chroma.py
依赖的「建集合 + 导入现成向量 + 查询」v2 契约。不依赖 chromadb 客户端库（避免 Rust 编译/网络问题）。

用法（在 Chroma v2 可达的机器上）：
    python chroma_v2_probe.py [--host 127.0.0.1] [--port 8000] [--dim 1024]

输出 PASS/FAIL：
    心跳(v2) → 建临时 collection → add(现成向量) → query → 清理
"""

import argparse
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = ""  # 运行时按 host/port 拼


def http(method, path, payload=None):
    url = BASE + path
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return -1, str(e)


def main() -> int:
    global BASE
    parser = argparse.ArgumentParser(description="Chroma v2 契约探针（纯 HTTP）")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--dim", type=int, default=1024)
    args = parser.parse_args()
    BASE = f"http://{args.host}:{args.port}"

    ok = True

    # ① 心跳（确认 /api/v2 可达）
    status, body = http("GET", "/api/v2/heartbeat")
    print(f"  [1] GET /api/v2/heartbeat -> {status} {body[:60]}")
    if status != 200:
        print("     ❌ /api/v2 不可达，请确认 Chroma v2 已启动、host/port 正确")
        ok = False

    # ② 建临时 collection（v2 返回含 id(UUID) + name）
    name = f"probe_{uuid.uuid4().hex[:8]}"
    path = ("/api/v2/tenants/default_tenant/databases/default_database/collections")
    status, body = http("POST", path, {"name": name})
    print(f"  [2] POST {path} ({name}) -> {status}")
    collection_id = None
    if status in (200, 201):
        try:
            collection_id = json.loads(body).get("id")
        except Exception:
            pass
        print(f"     collection id={collection_id}, name={name}")
    else:
        print(f"     body={body[:120]}")
        ok = False
    if not collection_id:
        print("     ❌ 未获得 collection id(UUID)，无法继续")
        ok = False
        return 1

    # ③ add 现成向量（v2 路径用 collection id）
    col_path = path + "/" + collection_id
    emb = [0.0] * args.dim
    emb[0] = 1.0
    add_body = {
        "ids": ["probe-1"],
        "embeddings": [emb],
        "documents": ["Chroma v2 探针测试文档"],
        "metadatas": [{"source_id": "doi:10.1000/probe", "title": "Probe"}],
    }
    status, body = http("POST", col_path + "/add", add_body)
    print(f"  [3] POST /collections/{{id}}/add -> {status} {body[:80]}")

    # ④ query（验证返回 ids/documents/metadatas）
    status, body = http("POST", col_path + "/query",
                        {"query_embeddings": [emb], "n_results": 1})
    print(f"  [4] POST {col_path}/query -> {status}")
    if status != 200:
        print(f"     body={body[:200]}")
        ok = False
    else:
        try:
            res = json.loads(body)
            ids = res.get("ids", [[]])[0]
            docs = res.get("documents", [[]])[0]
            metas = res.get("metadatas", [[]])[0]
            print(f"     ids={ids}")
            print(f"     documents[0]={str(docs[0])[:40] if docs else None}")
            print(f"     metadatas[0]={metas[0] if metas else None}")
            if not (ids and docs and metas):
                ok = False
                print("     ❌ query 未返回完整 ids/documents/metadatas")
        except Exception as e:
            print(f"     ❌ 解析响应失败: {e}")
            ok = False

    # ⑤ 清理临时 collection
    status, body = http("DELETE", col_path)
    print(f"  [5] DELETE {col_path} -> {status}")

    print("\n" + ("✅ PASS: Chroma v2 HTTP 契约（建/导/查）正常" if ok
                  else "❌ FAIL: 请根据上面输出修正 v2 契约（body/响应）。"))

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
