"""调用现有 embedding 服务（docker/embedding/server.py）生成向量。

请求与响应格式与该服务保持一致：{"model", "truncate", "input": [...]} →
{"model", "embeddings": [[...]]}；单次批量上限 16 条，与 Java 的 BATCH_SIZE 一致。
"""

import httpx

from .errors import unavailable

BATCH_SIZE = 16


class EmbedClient:
    def __init__(self, base_url: str, model: str, timeout: float):
        self._url = base_url.rstrip("/") + "/api/embed"
        self._model = model
        self._client = httpx.Client(timeout=timeout)

    def embed(self, texts: list[str]) -> list[list[float]]:
        vectors: list[list[float]] = []
        for start in range(0, len(texts), BATCH_SIZE):
            vectors.extend(self._embed_batch(texts[start : start + BATCH_SIZE]))
        if len(vectors) != len(texts):
            raise unavailable("向量模型返回了无效的向量")
        return vectors

    def _embed_batch(self, texts: list[str]) -> list[list[float]]:
        body = {"model": self._model, "truncate": False, "input": texts}
        try:
            response = self._client.post(self._url, json=body)
        except httpx.HTTPError as exc:
            raise unavailable("向量模型不可用或响应超时") from exc
        if response.status_code >= 400:
            raise unavailable(f"向量模型返回 HTTP {response.status_code}")
        try:
            embeddings = response.json().get("embeddings")
        except ValueError as exc:
            raise unavailable("向量模型返回了无效的响应") from exc
        if not isinstance(embeddings, list) or len(embeddings) != len(texts):
            raise unavailable("向量模型返回了无效的向量")
        for vector in embeddings:
            if not isinstance(vector, list) or not vector:
                raise unavailable("向量模型返回了空向量")
        return embeddings

    def dimension(self) -> int:
        return len(self.embed(["维度探测"])[0])
