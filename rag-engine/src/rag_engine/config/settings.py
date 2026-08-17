"""rag-engine 的类型安全环境配置。

配置优先级遵循 pydantic-settings：构造参数 > 环境变量 > ``.env`` > 默认值。
所有环境变量统一使用 ``RAG_ENGINE_`` 前缀，密钥型配置不得写入仓库。
"""

from __future__ import annotations

import os
from enum import StrEnum
from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Environment(StrEnum):
    """可观测性和安全策略使用的部署环境标识。"""

    LOCAL = "local"
    TEST = "test"
    DEVELOPMENT = "development"
    STAGING = "staging"
    PRODUCTION = "production"


class Settings(BaseSettings):
    """当前最小引擎实际消费的运行参数。

    未实现的外部 provider 不预置虚假的 endpoint/key 配置；对应 adapter 落地时，应在
    provider 自己的功能包中增加配置模型、启动校验和脱敏测试。
    """

    model_config = SettingsConfigDict(
        env_prefix="RAG_ENGINE_",
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    service_name: str = Field(default="rag-engine", min_length=1)
    service_version: str = Field(default="0.2.0-SNAPSHOT", min_length=1)
    environment: Environment = Environment.LOCAL
    host: str = Field(default="127.0.0.1", min_length=1)
    port: int = Field(default=8000, ge=1, le=65535)
    log_level: Literal["CRITICAL", "ERROR", "WARNING", "INFO", "DEBUG"] = "INFO"
    reload: bool = False
    docs_enabled: bool = True
    root_path: str = ""
    max_in_memory_tasks: int = Field(default=1024, ge=1, le=100_000)
    reranker_provider: Literal["local", "disabled"] = "local"

    # ------------------------------------------------------------------
    # 最小 RAG 链路运行配置（2026-08-17 新增）。
    # 约定：
    #   * 密钥型配置一律走环境变量，禁止写入仓库（RAG_ENGINE_* 前缀）；
    #   * pgvector_enabled=False 时容器保持 minimal（无外部连接），
    #     用于测试与「先内存跑通」的降级路径（fail-closed）。
    # ------------------------------------------------------------------

    # pgvector：向量写入/检索开关 + PostgreSQL 连接串（psycopg 连接参数）。
    pgvector_enabled: bool = True
    # 例 postgresql://user:pass@host:5432/ragkb；空串表示未配置 → 保持 minimal。
    database_url: str = ""

    # MinIO/S3：读取原始文档（object_key 即 S3 对象名，已含 tenant/时间前缀）。
    minio_endpoint: str = Field(default="http://localhost:9000", min_length=1)
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket: str = Field(default="kb-bucket-0814", min_length=1)

    # Embedding（OpenAI 兼容）：通义 DashScope text-embedding-v3（1024 维）。
    embedding_base_url: str = Field(
        default="https://dashscope.aliyuncs.com/compatible-mode/v1", min_length=1
    )
    embedding_api_key: str = ""
    embedding_model: str = Field(default="text-embedding-v3", min_length=1)
    embedding_dimension: int = Field(default=1024, ge=64, le=4096)

    # LLM（OpenAI 兼容，流式）：通义 DashScope qwen-plus。
    llm_base_url: str = Field(
        default="https://dashscope.aliyuncs.com/compatible-mode/v1", min_length=1
    )
    llm_api_key: str = ""
    llm_model: str = Field(default="qwen-plus", min_length=1)
    llm_timeout_ms: int = Field(default=120_000, ge=1_000, le=600_000)

    # 检索阈值：余弦相似度低于该值视为「无证据」，直接返回 NO_ANSWER 不调 LLM。
    retrieval_min_score: float = Field(default=0.30, ge=0.0, le=1.0)
    # 每批 Embedding 请求的文本条数（避免单请求过大）。
    embedding_batch_size: int = Field(default=64, ge=1, le=256)

    @property
    def phase(self) -> str:
        """返回当前能力阶段，供 liveness 和诊断日志使用。"""
        return "minimal"


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """加载并缓存进程级配置。

    ``RAG_ENGINE_ENV_FILE`` 只负责选择配置文件，不进入 Settings 字段，避免该路径被
    当作业务配置传播。未设置时读取当前工作目录的 ``.env``。
    """
    env_file = os.getenv("RAG_ENGINE_ENV_FILE")
    return Settings(_env_file=Path(env_file) if env_file else Path(".env"))


def clear_settings_cache() -> None:
    """清除设置缓存，供测试或受控的进程内重新装配使用。"""
    get_settings.cache_clear()
