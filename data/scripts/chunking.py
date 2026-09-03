#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""面向中英文科技文本的轻量语义分块器。"""

from dataclasses import dataclass
import re


@dataclass(frozen=True)
class Chunk:
    """原始文本中的一个分块及其半开区间偏移。"""

    text: str
    start: int
    end: int


_BOUNDARY_PATTERNS = (
    re.compile(r"\n\s*\n"),
    re.compile(r"\n"),
    re.compile(r"[。！？!?；;]"),
    re.compile(r"\s"),
)


def split_text(text: str, chunk_size: int = 512, overlap: int = 64) -> list[Chunk]:
    """按段落、换行、句末、空格优先级切分，必要时才按字符截断。"""
    if chunk_size <= 0:
        raise ValueError("chunk_size 必须大于 0")
    if overlap < 0 or overlap >= chunk_size:
        raise ValueError("overlap 必须满足 0 <= overlap < chunk_size")
    if not text or not text.strip():
        return []

    chunks: list[Chunk] = []
    start = 0
    length = len(text)
    while start < length:
        while start < length and text[start].isspace():
            start += 1
        if start >= length:
            break

        limit = min(start + chunk_size, length)
        end = limit
        if limit < length:
            end = _best_boundary(text, start, limit) or limit

        raw = text[start:end]
        left_trimmed = len(raw) - len(raw.lstrip())
        right_trimmed = len(raw) - len(raw.rstrip())
        chunk_start = start + left_trimmed
        chunk_end = end - right_trimmed
        if chunk_start < chunk_end:
            chunks.append(Chunk(text[chunk_start:chunk_end], chunk_start, chunk_end))

        if end >= length:
            break
        # 保留字符重叠；下一轮仍会跳过无意义的空白。
        start = max(end - overlap, start + 1)
    return chunks


def _best_boundary(text: str, start: int, limit: int) -> int | None:
    """返回窗口内最靠后的高优先级边界（包含句末符号）。"""
    window = text[start:limit]
    for pattern in _BOUNDARY_PATTERNS:
        matches = list(pattern.finditer(window))
        if matches:
            return start + matches[-1].end()
    return None
