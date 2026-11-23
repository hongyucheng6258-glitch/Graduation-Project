"""ai-service 入口。

对外只有 Java 后端一个调用方：服务不发布宿主机端口，所有业务接口要求
X-Service-Token。文档正文与切片仍然由 Java 写入 MySQL，本服务只做解析、向量化、
向量检索与向量清理。
"""

import base64
import binascii
import hmac
import time
import uuid
from threading import BoundedSemaphore

from fastapi import Depends, FastAPI, Header, Request
from fastapi.responses import JSONResponse
from starlette.concurrency import run_in_threadpool

from . import pdf_chunker
from .config import Settings, load_settings
from .embed_client import EmbedClient
from .errors import RagError
from .schemas import (
    Chunk,
    DeleteRequest,
    DeleteResponse,
    IndexRequest,
    IndexResponse,
    SearchRequest,
    SearchResponse,
)
from .vector_store import VectorStore

SETTINGS: Settings = load_settings()
EMBEDDER = EmbedClient(SETTINGS.embedding_url, SETTINGS.embedding_model, SETTINGS.embedding_timeout)
STORE = VectorStore(
    SETTINGS.qdrant_url, SETTINGS.collection, SETTINGS.top_k, SETTINGS.min_score, SETTINGS.qdrant_timeout
)
# 解析与向量化属于 CPU 密集型任务，限制并发避免抢占同机 MySQL/后端的资源
JOBS = BoundedSemaphore(SETTINGS.max_concurrent_jobs)

app = FastAPI(title="campus-ai-service", version="1.0.0", docs_url=None, redoc_url=None, openapi_url=None)


@app.exception_handler(RagError)
async def _handle_rag_error(_request: Request, exc: RagError) -> JSONResponse:
    return JSONResponse(status_code=exc.status, content={"error": exc.message})


@app.exception_handler(ValueError)
async def _handle_value_error(_request: Request, exc: ValueError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"error": str(exc)})


def require_token(x_service_token: str = Header(default="")) -> None:
    if not hmac.compare_digest(x_service_token, SETTINGS.service_token):
        raise RagError(401, "服务令牌无效")


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ready"}


@app.post("/internal/rag/index", response_model=IndexResponse, dependencies=[Depends(require_token)])
async def index(payload: IndexRequest) -> IndexResponse:
    if not JOBS.acquire(blocking=False):
        raise RagError(503, "AI 服务繁忙，请稍后重试")
    try:
        chunk_chars = payload.chunkChars or SETTINGS.chunk_chars
        content = _decode(payload.fileBase64)
        started = time.monotonic()
        parts = await run_in_threadpool(pdf_chunker.split_pdf, content, chunk_chars)
        texts = [text for _, text in parts]
        ids = [str(uuid.uuid4()) for _ in parts]
        vectors = await run_in_threadpool(EMBEDDER.embed, texts)
        await run_in_threadpool(
            STORE.upsert,
            payload.documentId,
            payload.ownerId,
            payload.scope,
            payload.version,
            ids,
            vectors,
        )
        chunks = [Chunk(id=ids[position], page=parts[position][0], text=parts[position][1]) for position in range(len(parts))]
        return IndexResponse(
            documentId=payload.documentId,
            chunks=chunks,
            chunksCount=len(chunks),
            elapsedMs=int((time.monotonic() - started) * 1000),
        )
    finally:
        JOBS.release()


@app.post("/internal/rag/search", response_model=SearchResponse, dependencies=[Depends(require_token)])
async def search(payload: SearchRequest) -> SearchResponse:
    question = _truncate(payload.question, SETTINGS.max_query_chars)
    vectors = await run_in_threadpool(EMBEDDER.embed, [question])
    ids = await run_in_threadpool(
        STORE.search, payload.userId, payload.documentId, vectors[0], payload.internal
    )
    return SearchResponse(ids=ids)


@app.delete("/internal/rag/collection", response_model=DeleteResponse, dependencies=[Depends(require_token)])
async def remove(payload: DeleteRequest) -> DeleteResponse:
    await run_in_threadpool(STORE.delete, payload.documentId, payload.version)
    return DeleteResponse(deleted=True)


def _decode(file_base64: str) -> bytes:
    raw = file_base64.split(";base64,", 1)[-1]
    try:
        content = base64.b64decode(raw, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise RagError(400, "PDF 文件内容无效") from exc
    if not content:
        raise RagError(400, "PDF 文件内容无效")
    return content


def _truncate(text: str, limit: int) -> str:
    """按码点截断，与 Java 的 offsetByCodePoints 行为一致。"""
    return text[:limit] if len(text) > limit else text
