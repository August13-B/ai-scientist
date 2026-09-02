#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
学术 PDF 批量解析脚本（骨架）。

职责：批量解析目标领域 PDF 论文，提取标题、摘要、章节、参考文献，
输出结构化文本供向量化灌库（论文库）。

TODO（马梓涵）：
- 接入 Grobid 做学术 PDF 结构化解析（标题/摘要/章节/参考文献）
- 清洗无关字符（页眉页脚、公式噪声等）
- 按 RecursiveCharacterTextSplitter 分块（chunk_size=512, overlap=64）
- 输出格式与向量库灌库脚本对接（见 docs/rag.md）

用法：
    python scripts/pdf_parser.py --input ./pdfs --output ./processed
"""

import argparse
from pathlib import Path


def parse_pdf(pdf_path: Path) -> str:
    """解析单个 PDF，返回提取的纯文本。"""
    # TODO: 使用 pdfplumber / pymupdf / Grobid 提取结构化文本
    return ""


def split_chunks(text: str, chunk_size: int = 512, overlap: int = 64):
    """按语义保持分块（RecursiveCharacterTextSplitter 风格）。"""
    # TODO: 实现分块逻辑
    return []


def main() -> None:
    parser = argparse.ArgumentParser(description="学术 PDF 批量解析")
    parser.add_argument("--input", required=True, help="PDF 目录")
    parser.add_argument("--output", required=True, help="输出目录")
    args = parser.parse_args()

    input_dir = Path(args.input)
    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    for pdf in input_dir.glob("*.pdf"):
        text = parse_pdf(pdf)
        chunks = split_chunks(text)
        # TODO: 输出结构化 JSON（元数据 + 分块），供向量化灌库
        print(f"[OK] {pdf.name}: {len(chunks)} chunks")


if __name__ == "__main__":
    main()
