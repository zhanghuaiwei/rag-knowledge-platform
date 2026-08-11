"""内部 HTTP API 共用的 Pydantic 基类和知识库配置 DTO。"""

from pydantic import BaseModel, ConfigDict, Field


def to_camel(field_name: str) -> str:
    """将 Python ``snake_case`` 字段转换为 OpenAPI 使用的 ``camelCase``。"""
    head, *tail = field_name.split("_")
    return head + "".join(part.capitalize() for part in tail)


class ApiModel(BaseModel):
    """内部 API DTO 基类：拒绝未知字段并统一别名策略。"""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


class KbConfig(ApiModel):
    """v0.1 知识库 RAG 参数；生产配置应由不可变 index profile 管理。"""

    embedding_model: str = Field(default="bge-m3", min_length=1)
    chunk_size: int = Field(default=512, ge=1)
    chunk_overlap: int = Field(default=50, ge=0)
    top_k: int = Field(default=5, ge=1, le=100)
    reranker_enabled: bool = True
