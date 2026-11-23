"""Java / Python 切片等价性比对。

用 tests/ChunkRef.java（与 PdfChunker 相同的切分循环，去掉 PDFBox 依赖）在随机
语料上逐段比对。需要 JDK 才能运行，因此在 CI 中是可选项：

    python3 tests/compare_with_java.py

比对通过即说明两种实现在同一份文档上产出相同的段数与段文本。
"""

import base64
import random
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.pdf_chunker import split_text  # noqa: E402

HERE = Path(__file__).resolve().parent
CASES = 120
SEPARATOR = "\u0001"


def build_corpus() -> list[tuple[int, str]]:
    rng = random.Random(20261123)
    alphabet = "校园向导服务活动报名闲置互换失物认领学习互助知识库检索学生教师管理员"
    corpus = []
    for index in range(CASES):
        size = rng.choice([160, 320, 640])
        length = rng.choice([0, 30, 159, 160, 500, 640, 641, 900, 1500, 2400, 4000])
        pieces = []
        while sum(len(piece) for piece in pieces) < length:
            token = "".join(rng.choice(alphabet) for _ in range(rng.randint(1, 40)))
            pieces.append(token)
            pieces.append(rng.choice(["。", "。", " ", "\n", "；", ""]))
        text = "".join(pieces)[:length]
        corpus.append((size, text))
    return corpus


def java_parts(jar_dir: Path, corpus: list[tuple[int, str]]) -> list[list[str]]:
    payload = "\n".join(
        f"{size}\t{base64.b64encode(text.encode()).decode()}" for size, text in corpus
    )
    completed = subprocess.run(
        [
            "java",
            "-cp",
            str(jar_dir),
            "ChunkRef",
        ],
        input=payload,
        capture_output=True,
        text=True,
        check=True,
    )
    result = []
    for line in completed.stdout.splitlines():
        parts = [] if line == "" else [
            base64.b64decode(field).decode() for field in line.split(SEPARATOR)
        ]
        result.append(parts)
    return result


def main() -> int:
    jar_dir = HERE / "build"
    jar_dir.mkdir(exist_ok=True)
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(jar_dir), str(HERE / "ChunkRef.java")], check=True)
    corpus = build_corpus()
    expected = java_parts(jar_dir, corpus)
    mismatches = 0
    for position, (size, text) in enumerate(corpus):
        actual = split_text(text, size)
        if actual != expected[position]:
            mismatches += 1
            print(f"case {position} size={size} length={len(text)}")
            print(f"  java   : {[len(part) for part in expected[position]]}")
            print(f"  python : {[len(part) for part in actual]}")
    print(f"比对 {len(corpus)} 例，差异 {mismatches} 例")
    return 1 if mismatches else 0


if __name__ == "__main__":
    raise SystemExit(main())
