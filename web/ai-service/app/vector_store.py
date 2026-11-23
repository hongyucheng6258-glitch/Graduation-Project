"""Qdrant 向量读写。

集合独立于 Java 现有集合（默认 campus_bge_small_onnx_v1_py），payload 字段与现有
实现完全一致：doc_id / owner_id / scope / version。检索过滤规则与 Java 的
KnowledgeVectorClient.search 对齐，返回 point id 列表，正文仍由 Java 从 MySQL 取，
保证 MySQL 是切片文本的唯一真源。
"""

from qdrant_client import QdrantClient
from qdrant_client import models

from .errors import unavailable

SCOPES_PUBLIC = "PUBLIC"
SCOPES_PRIVATE = "PRIVATE"
SCOPES_ADMIN = "ADMIN"


def _condition(key: str, value) -> models.FieldCondition:
    return models.FieldCondition(key=key, match=models.MatchValue(value=value))


class VectorStore:
    def __init__(self, base_url: str, collection: str, top_k: int, min_score: float, timeout: float):
        self._client = QdrantClient(url=base_url, timeout=timeout)
        self._collection = collection
        self._top_k = top_k
        self._min_score = min_score
        self._ready = False

    def ensure_collection(self, size: int) -> None:
        if self._ready:
            return
        try:
            if not self._client.collection_exists(self._collection):
                self._client.create_collection(
                    self._collection,
                    vectors_config=models.VectorParams(size=size, distance=models.Distance.COSINE),
                )
            info = self._client.get_collection(self._collection)
        except Exception as exc:  # noqa: BLE001 - 客户端异常类型不稳定
            raise unavailable("向量数据库不可用") from exc
        actual = info.config.params.vectors.size
        if actual != size:
            raise unavailable("向量维度不匹配，请使用对应的知识库集合并重建索引")
        self._ready = True

    def upsert(
        self,
        document_id: int,
        owner_id: int,
        scope: str,
        version: str,
        ids: list[str],
        vectors: list[list[float]],
    ) -> None:
        if not ids:
            return
        self.ensure_collection(len(vectors[0]))
        points = [
            models.PointStruct(
                id=ids[index],
                vector=vectors[index],
                payload={
                    "doc_id": document_id,
                    "owner_id": owner_id,
                    "scope": scope,
                    "version": version,
                },
            )
            for index in range(len(ids))
        ]
        try:
            self._client.upsert(self._collection, points=points, wait=True)
        except Exception as exc:  # noqa: BLE001
            raise unavailable("向量数据库写入失败") from exc

    def search(
        self, user_id: int, document_id: int | None, vector: list[float], internal: bool
    ) -> list[str]:
        try:
            query = self._client.query_points(
                self._collection,
                query=vector,
                limit=self._top_k,
                score_threshold=self._min_score,
                query_filter=self._filter(user_id, document_id, internal),
                with_payload=False,
                with_vectors=False,
            )
        except Exception as exc:  # noqa: BLE001
            raise unavailable("向量数据库不可用") from exc
        return [str(point.id) for point in query.points]

    def delete(self, document_id: int, version: str | None) -> None:
        must = [_condition("doc_id", document_id)]
        if version is not None:
            must.append(_condition("version", version))
        try:
            self._client.delete(
                self._collection,
                points_selector=models.FilterSelector(filter=models.Filter(must=must)),
                wait=True,
            )
        except Exception as exc:  # noqa: BLE001
            raise unavailable("向量数据库删除失败") from exc

    def _filter(self, user_id: int, document_id: int | None, internal: bool) -> models.Filter:
        if internal:
            must: list = [_condition("scope", SCOPES_ADMIN)]
        else:
            must = []
        # 与 Java 一致：should 中任一命中即可；私有的还要求 owner_id 匹配
        should = [
            _condition("scope", SCOPES_PUBLIC),
            models.Filter(must=[_condition("scope", SCOPES_PRIVATE), _condition("owner_id", user_id)]),
        ]
        if document_id is not None:
            must.append(_condition("doc_id", document_id))
        if internal:
            return models.Filter(must=must)
        return models.Filter(must=must or None, should=should)
