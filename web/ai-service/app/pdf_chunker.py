"""PDF 文本提取与切片。

切片规则必须与 Java 侧 ``PdfChunker`` 保持一致，否则同一份 PDF 在两种实现下的
页码归属和重叠字符会漂移，引用页码随之偏移。保持一致的部分：

- 每页独立切片，页码从 1 开始；
- 页内空白折叠为单个空格并去除首尾空白；
- 段长 160 至 640 字符，默认 640，段间重叠 80 字符；
- 优先在句号或空格处断开，断点须超过段长一半才采用；
- 单个 PDF 最多 200 页、500 段。

Java 用 UTF-16 码元遍历，因此额外做了高/低代理对修正；Python 字符串按码点操作，
不存在切断代理对的问题，两者结果等价。
"""

import re

MAX_PAGES = 200
MAX_PARTS = 500
OVERLAP = 80
DEFAULT_SIZE = 640

_WHITESPACE = re.compile(r"\s+")
_PART_ERROR_PAGES = "知识库 PDF 最多支持 200 页"
_PART_ERROR_LENGTH = "PDF 可索引文本过长，请拆分后上传"
_PART_ERROR_EMPTY = "PDF 未提取到可索引文本"


def normalize(text: str) -> str:
    """与 Java 的 replaceAll("\\s+", " ").strip() 等价。"""
    return _WHITESPACE.sub(" ", text or "").strip()


def split_text(text: str, chunk_size: int = DEFAULT_SIZE) -> list[str]:
    """把一页文本切成若干段，返回去除首尾空白后的段列表。"""
    if not 160 <= chunk_size <= 640:
        raise ValueError("PDF 索引分段长度须在 160 至 640 字符之间")
    body = normalize(text)
    if not body:
        return []
    parts: list[str] = []
    start = 0
    length = len(body)
    while start < length:
        end = min(start + chunk_size, length)
        if end < length:
            # Java 的 lastIndexOf(ch, end - 1) 等价于在 [0, end) 内找最后一次出现
            boundary = max(body.rfind("。", 0, end), body.rfind(" ", 0, end))
            if boundary > start + chunk_size // 2:
                end = boundary + 1
        parts.append(body[start:end].strip())
        if len(parts) > MAX_PARTS:
            raise ValueError(_PART_ERROR_LENGTH)
        if end >= length:
            break
        start = end - OVERLAP
    return parts


def split_pdf(content: bytes, chunk_size: int = DEFAULT_SIZE) -> list[tuple[int, str]]:
    """解析 PDF 并返回 (页码, 段落文本) 列表。"""
    import pymupdf  # PyMuPDF，延迟导入以便切片逻辑可独立测试

    if not 160 <= chunk_size <= 640:
        raise ValueError("PDF 索引分段长度须在 160 至 640 字符之间")
    try:
        document = pymupdf.open(stream=content, filetype="pdf")
    except Exception as exc:  # noqa: BLE001 - PDF 库抛出的异常类型不稳定
        raise ValueError("PDF 文件内容无效") from exc

    with document:
        if document.page_count > MAX_PAGES:
            raise ValueError(_PART_ERROR_PAGES)
        parts: list[tuple[int, str]] = []
        for index in range(document.page_count):
            page = document.load_page(index)
            text = page.get_text("text", sort=True)
            for chunk in split_text(text, chunk_size):
                parts.append((index + 1, chunk))
                if len(parts) > MAX_PARTS:
                    raise ValueError(_PART_ERROR_LENGTH)
    if not parts:
        raise ValueError(_PART_ERROR_EMPTY)
    return parts
