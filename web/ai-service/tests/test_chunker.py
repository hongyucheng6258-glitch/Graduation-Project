"""切片行为测试。

这些用例固化 Java ``PdfChunker`` 的切片规则。两种实现必须同时通过，
否则同一份 PDF 的页码归属会漂移，答案引用随之偏移。

直接运行：python3 -m unittest discover -s tests -t .
"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import pdf_chunker  # noqa: E402


class NormalizeTest(unittest.TestCase):
    def test_collapses_whitespace(self) -> None:
        self.assertEqual(pdf_chunker.normalize("  校园\n\t向导  服务  "), "校园 向导 服务")

    def test_empty(self) -> None:
        self.assertEqual(pdf_chunker.normalize(None), "")


class SplitTextTest(unittest.TestCase):
    def test_rejects_invalid_chunk_size(self) -> None:
        for size in (159, 641):
            with self.assertRaises(ValueError):
                pdf_chunker.split_text("内容", size)

    def test_empty_text_gives_no_parts(self) -> None:
        self.assertEqual(pdf_chunker.split_text("   ", 640), [])

    def test_short_text_is_single_part(self) -> None:
        self.assertEqual(pdf_chunker.split_text("只有一句话。", 640), ["只有一句话。"])

    def test_hard_cut_keeps_overlap(self) -> None:
        body = "字" * 1500
        parts = pdf_chunker.split_text(body, 640)
        self.assertEqual(len(parts), 3)
        for part in parts:
            self.assertLessEqual(len(part), 640)
        self.assertEqual(parts[0], body[:640])
        self.assertEqual(parts[1][:80], parts[0][-80:])
        self.assertEqual(parts[1], body[560:1200])
        self.assertEqual(parts[2], body[1120:1500])

    def test_prefers_sentence_boundary(self) -> None:
        body = "甲" * 400 + "。" + "乙" * 499
        parts = pdf_chunker.split_text(body, 640)
        self.assertEqual(parts[0], "甲" * 400 + "。")

    def test_ignores_boundary_before_half_chunk(self) -> None:
        # 句号出现在段长一半之前时不采用，直接硬切
        body = "甲" * 100 + "。" + "乙" * 899
        parts = pdf_chunker.split_text(body, 640)
        self.assertEqual(parts[0], body[:640])

    def test_rejects_too_many_parts(self) -> None:
        with self.assertRaises(ValueError):
            pdf_chunker.split_text("字" * (641 * 600), 160)


class PageSplitTest(unittest.TestCase):
    def test_max_pages_constant(self) -> None:
        self.assertEqual(pdf_chunker.MAX_PAGES, 200)
        self.assertEqual(pdf_chunker.MAX_PARTS, 500)
        self.assertEqual(pdf_chunker.OVERLAP, 80)


if __name__ == "__main__":
    unittest.main()
