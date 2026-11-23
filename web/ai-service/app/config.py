"""运行配置。所有键都有默认值，除服务令牌外均可直接启动。"""

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(case_sensitive=False, extra="ignore")

    # 服务自身
    service_token: str = Field("", validation_alias="CAMPUS_AI_SERVICE_TOKEN")
    max_concurrent_jobs: int = Field(2, validation_alias="CAMPUS_AI_MAX_JOBS")
    log_level: str = Field("info", validation_alias="CAMPUS_AI_LOG_LEVEL")

    # 复用现有 embedding 服务（docker/embedding，端口 11434），不修改该服务
    embedding_url: str = Field("http://embedding:11434", validation_alias="CAMPUS_AGENT_RAG_EMBEDDING_URL")
    embedding_model: str = Field(
        "bge-small-zh-v1.5-onnx", validation_alias="CAMPUS_AGENT_RAG_EMBEDDING_MODEL"
    )

    # 向量库：使用独立集合，与 Java 现有集合隔离
    qdrant_url: str = Field("http://qdrant:6333", validation_alias="CAMPUS_AGENT_RAG_QDRANT_URL")
    collection: str = Field("campus_bge_small_onnx_v1_py", validation_alias="CAMPUS_AI_RAG_COLLECTION")

    # 检索参数，需与 Java 侧 campus.agent.rag.* 保持一致
    chunk_chars: int = Field(640, validation_alias="CAMPUS_AGENT_RAG_CHUNK_CHARS")
    top_k: int = Field(15, validation_alias="CAMPUS_AI_RAG_TOP_K")
    min_score: float = Field(0.40, validation_alias="CAMPUS_AGENT_RAG_MIN_SCORE")
    max_query_chars: int = Field(2200, validation_alias="CAMPUS_AGENT_RAG_QUERY_CHARS")

    # 外部调用超时（秒）
    embedding_timeout: float = Field(20.0, validation_alias="CAMPUS_AI_EMBEDDING_TIMEOUT")
    qdrant_timeout: float = Field(20.0, validation_alias="CAMPUS_AI_QDRANT_TIMEOUT")


def load_settings() -> Settings:
    settings = Settings()
    if not settings.service_token:
        raise RuntimeError("CAMPUS_AI_SERVICE_TOKEN 未配置，拒绝以无鉴权方式启动")
    if not 160 <= settings.chunk_chars <= 640:
        raise RuntimeError("CAMPUS_AGENT_RAG_CHUNK_CHARS 需在 160 至 640 之间")
    if not settings.collection.isascii() or not settings.collection.replace("-", "").replace("_", "").isalnum():
        raise RuntimeError("向量集合名无效")
    return settings
