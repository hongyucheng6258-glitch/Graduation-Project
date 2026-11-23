"""真实 PDF 的跨语言切片比对。

用项目真实的 PdfChunker（PDFBox）与 Python 实现分别处理同一份 PDF，逐段比对页码与文本。
这是方案第 10 节"切片一致性"的可执行版本：两种 PDF 提取器（PDFBox 与 PyMuPDF）对同一页
可能给出不同的空白与行序，该脚本用于量化差异，而不是假设差异不存在。

用法：
    python3 tests/compare_pdf.py <pdf 路径> [段长]
"""

import base64
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.pdf_chunker import split_pdf  # noqa: E402

HERE = Path(__file__).resolve().parent
BUILD = HERE / "build"
CLASSPATH = BUILD / "classes"


def ensure_java_sources() -> None:
    """编译比对工具。需要项目已构建过的 jar 或 classes 提供 PdfChunker 与 PDFBox。"""
    jar = HERE.parents[2] / "web" / "backend" / "target" / "platform-server.jar"
    if not jar.exists():
        raise SystemExit(f"未找到 {jar}，请先构建后端（mvn package）")
    extract = BUILD / "extract"
    if not CLASSPATH.exists():
        subprocess.run(["unzip", "-q", str(jar), "BOOT-INF/classes/*", "BOOT-INF/lib/*", "-d", str(extract)], check=True)
    CLASSPATH.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            "javac",
            "-encoding",
            "UTF-8",
            "-cp",
            f"{extract / 'BOOT-INF' / 'classes'}:{extract / 'BOOT-INF' / 'lib'}/*",
            "-d",
            str(CLASSPATH),
            str(HERE / "ComparePdf.java"),
        ],
        check=True,
    )


def java_parts(pdf: str, chunk_size: int) -> list[tuple[int, str]]:
    extract = BUILD / "extract"
    completed = subprocess.run(
        [
            "java",
            "-cp",
            f"{CLASSPATH}:{extract / 'BOOT-INF' / 'classes'}:{extract / 'BOOT-INF' / 'lib'}/*",
            "com.campus.platform.module.ai.rag.ComparePdf",
            pdf,
            str(chunk_size),
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    parts = []
    skipped = 0
    for line in completed.stdout.splitlines():
        if not line:
            continue
        # PDFBox 的 WARN 日志会写到 stdout，按"页码 + base64"格式过滤
        fields = line.split("\t", 1)
        if len(fields) != 2 or not fields[0].isdigit():
            skipped += 1
            continue
        parts.append((int(fields[0]), base64.b64decode(fields[1]).decode()))
    if skipped:
        print(f"已忽略 {skipped} 行非切片输出（PDFBox 日志）")
    return parts


def main() -> int:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    pdf = sys.argv[1]
    chunk_size = int(sys.argv[2]) if len(sys.argv) > 2 else 400
    ensure_java_sources()
    expected = java_parts(pdf, chunk_size)
    actual = split_pdf(Path(pdf).read_bytes(), chunk_size)

    print(f"java 段数={len(expected)} python 段数={len(actual)}")
    same_count = len(expected) == len(actual)
    same_pages = same_count and [page for page, _ in expected] == [page for page, _ in actual]
    same_text = same_count and [text for _, text in expected] == [text for _, text in actual]
    print(f"段数一致={same_count} 页码一致={same_pages} 文本一致={same_text}")

    for index in range(min(len(expected), len(actual))):
        if expected[index] != actual[index]:
            print(f"首个差异段 {index}：java 页 {expected[index][0]} 长 {len(expected[index][1])}，"
                  f"python 页 {actual[index][0]} 长 {len(actual[index][1])}")
            print(f"  java   : {expected[index][1][:60]!r}")
            print(f"  python : {actual[index][1][:60]!r}")
            break
    return 0 if same_text else 1


if __name__ == "__main__":
    raise SystemExit(main())
