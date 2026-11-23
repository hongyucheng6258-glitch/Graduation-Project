"""内部接口的请求与响应模型（仅容器内网调用）。"""

from pydantic import BaseModel, Field


class IndexRequest(BaseModel):
    documentId: int
    ownerId: int
    scope: str
    version: str = Field(min_length=1, max_length=64)
    fileName: str = ""
    fileBase64: str
    chunkChars: int | None = None


class Chunk(BaseModel):
    id: str
    page: int
    text: str


class IndexResponse(BaseModel):
    documentId: int
    chunks: list[Chunk]
    chunksCount: int
    elapsedMs: int


class SearchRequest(BaseModel):
    question: str = Field(min_length=1)
    userId: int = 0
    documentId: int | None = None
    internal: bool = False
    topK: int | None = None
    minScore: float | None = None


class SearchResponse(BaseModel):
    ids: list[str]


class DeleteRequest(BaseModel):
    documentId: int
    version: str | None = None


class DeleteResponse(BaseModel):
    deleted: bool
