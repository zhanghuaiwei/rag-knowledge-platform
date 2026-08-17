"""rag-engine 的类型安全环境配置。

配置优先级遵循 pydantic-settings（从高到低）：
    构造参数 > 环境变量 > ``.env`` > provider_type 预设 > 默认值。

所有环境变量统一使用 ``RAG_ENGINE_`` 前缀，密钥型配置不得写入仓库。

provider 路由约定：
    每种能力（llm / embedding / reranker / safety）都通过独立的 ``*_PROVIDER_TYPE``
    选择适配器实现。同一个 provider 类型对应一个预设的 base_url 和默认 model，用户
    显式设置 ``*_BASE_URL`` 或 ``*_MODEL`` 时以用户配置为准（强制覆盖）。
"""

from __future__ import annotations

import os
from enum import StrEnum
from functools import lru_cache
from pathlib import Path
from typing import Literal

from pydantic import Field, field_validator, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Environment(StrEnum):
    """可观测性和安全策略使用的部署环境标识。"""

    LOCAL = "local"
    TEST = "test"
    DEVELOPMENT = "development"
    STAGING = "staging"
    PRODUCTION = "production"


# ---------------------------------------------------------------------------
# provider 类型枚举（单一事实来源：所有路由决策都基于这些枚举值）
# ---------------------------------------------------------------------------

class LlmProviderType(StrEnum):
    """LLM 适配器类型。

    ``openai_compatible`` 是通用兜底：通义 DashScope（compatible-mode）、硅基流动、
    DeepSeek、智谱、火山方舟、月之暗面等 OpenAI 兼容网关都用该类型；
    其余具体值仅用于加载**预设的 base_url + 默认 model**，让用户少填配置。
    """

    OPENAI_COMPATIBLE = "openai_compatible"
    DASHSCOPE = "dashscope"          # 阿里云通义 DashScope
    SILICONFLOW = "siliconflow"      # 硅基流动
    DEEPSEEK = "deepseek"            # DeepSeek
    ZHIPU = "zhipu"                  # 智谱 AI
    ANTHROPIC = "anthropic"          # Anthropic 原生（延迟导入 SDK）
    NOOP = "noop"                    # 无模型占位（开发/降级/测试，永远返回 NO_ANSWER）


class EmbeddingProviderType(StrEnum):
    """Embedding 适配器类型。语义同 :class:`LlmProviderType`。"""

    OPENAI_COMPATIBLE = "openai_compatible"
    DASHSCOPE = "dashscope"
    SILICONFLOW = "siliconflow"
    ZHIPU = "zhipu"
    NOOP = "noop"                    # 无 embedding 占位（测试，返回零向量）


class RerankerProviderType(StrEnum):
    RERANKER_PROVIDER_TYPE = "local"
    DISABLED = "disabled"
    SILICONFLOW = "siliconflow"
    NOOP = "noop"


class SafetyProviderType(StrEnum):
    NOOP = "noop"                    # 永远放行（开发/演示）
    DISABLED = "disabled"


# ---------------------------------------------------------------------------
# provider_type → 预设 base_url + 默认 model 映射表
# 用户显式设置了 *_BASE_URL 或 *_MODEL 时以用户配置为准，不走预设。
# ---------------------------------------------------------------------------

_LLM_PRESETS: dict[str, dict[str, str]] = {
    LlmProviderType.OPENAI_COMPATIBLE: {
        "base_url": "",  # 空 → 让 OpenAI SDK 用默认（不推荐；应选具体厂商）
        "model": "",     # 空 → 由调用方传
    },
    LlmProviderType.DASHSCOPE: {
        "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "qwen-plus",
    },
    LlmProviderType.SILICONFLOW: {
        "base_url": "https://api.siliconflow.cn/v1",
        "model": "Qwen/Qwen2.5-72B-Instruct",
    },
    LlmProviderType.DEEPSEEK: {
        "base_url": "https://api.deepseek.com/v1",
        "model": "deepseek-chat",
    },
    LlmProviderType.ZHIPU: {
        "base_url": "https://open.bigmodel.cn/api/paas/v4",
        "model": "glm-4-flash",
    },
    LlmProviderType.ANTHROPIC: {
        # Anthropic 原生 SDK base_url 是固定的，这里填的是 OpenAI 兼容模式的 fallback
        # （当用户同时显式设置了 LLM_BASE_URL 时生效）。原生模式不读该字段。
        "base_url": "https://api.anthropic.com/v1",
        "model": "claude-3-5-sonnet-latest",
    },
    LlmProviderType.NOOP: {
        "base_url": "",
        "model": "noop",
    },
}

_EMBEDDING_PRESETS: dict[str, dict[str, object]] = {
    EmbeddingProviderType.OPENAI_COMPATIBLE: {"base_url": "", "model": "", "dimension": 1024},
    EmbeddingProviderType.DASHSCOPE: {
        "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "model": "text-embedding-v3",
        "dimension": 1024,
    },
    EmbeddingProviderType.SILICONFLOW: {
        "base_url": "https://api.siliconflow.cn/v1",
        "model": "BAAI/bge-m3",
        "dimension": 1024,
    },
    EmbeddingProviderType.ZHIPU: {
        "base_url": "https://open.bigmodel.cn/api/paas/v4",
        "model": "embedding-3",
        "dimension": 1024,
    },
    EmbeddingProviderType.NOOP: {
        "base_url": "",
        "model": "noop",
        "dimension": 1024,
    },
}


# ---------------------------------------------------------------------------
# 辅助：判断环境变量/构造参数是否被显式设置
# ---------------------------------------------------------------------------

def _is_explicitly_set(env_name: str, constructor_override: object | None = None) -> bool:
    """True 表示用户显式传入了该字段（不管值），False 表示用 Settings 类的默认值。

    判定顺序：
    1. 构造参数直接传入 → 视为显式设置
    2. 环境变量 ``RAG_ENGINE_<UPPER_FIELD>`` 存在且非空 → 视为显式设置
    3. 否则 → 默认值
    """
    if constructor_override is not None:
        return True
    value = os.getenv(env_name)
    return bool(value)  # 空串或 None 视为未显式设置


class Settings(BaseSettings):
    """运行时配置模型。"""

    model_config = SettingsConfigDict(
        env_prefix="RAG_ENGINE_",
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ---- 通用 -----------------------------------------------------------------

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

    # ---- 存储 -----------------------------------------------------------------

    pgvector_enabled: bool = True
    database_url: str = ""

    minio_endpoint: str = Field(default="http://localhost:9000", min_length=1)
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket: str = Field(default="kb-bucket-0814", min_length=1)

    # ---- 检索阈值 + 批量大小 --------------------------------------------------

    retrieval_min_score: float = Field(default=0.30, ge=0.0, le=1.0)
    embedding_batch_size: int = Field(default=64, ge=1, le=256)

    # ---- LLM provider 路由 ----------------------------------------------------

    llm_provider_type: LlmProviderType = LlmProviderType.DASHSCOPE
    llm_base_url: str = ""          # 空 → 从 provider_type 预设读取
    llm_api_key: str = ""            # 留空表示该 provider 未配置，container 会回退 minimal
    llm_model: str = ""              # 空 → 从 provider_type 预设读取
    llm_timeout_ms: int = Field(default=120_000, ge=1_000, le=600_000)
    # 可选：部分网关需要自定义 header（如代理鉴权、租户标识、请求追踪）
    # 格式：逗号分隔 "Header1:Val1,Header2:Val2"；空串表示无自定义请求头
    llm_extra_headers: str = ""

    # ---- Embedding provider 路由 ----------------------------------------------

    embedding_provider_type: EmbeddingProviderType = EmbeddingProviderType.DASHSCOPE
    embedding_base_url: str = ""
    embedding_api_key: str = ""
    embedding_model: str = ""
    embedding_dimension: int = 0     # 0 → 从 provider_type 预设读取
    embedding_extra_headers: str = ""

    # ---- Reranker provider 路由（保持 backward-compat：reranker_provider 映射到新字段）----

    reranker_provider_type: RerankerProviderType = RerankerProviderType.RERANKER_PROVIDER_TYPE
    # backward-compat alias：老环境变量 RAG_ENGINE_RERANKER_PROVIDER 仍可使用
    reranker_provider: Literal["local", "disabled"] = "local"

    # ---- Safety provider 路由 -------------------------------------------------

    safety_provider_type: SafetyProviderType = SafetyProviderType.NOOP

    # =========================================================================
    # 校验 + provider 预设注入（model_validator 在字段全部解析完后运行）
    # =========================================================================

    @field_validator("reranker_provider_type", mode="before")
    @classmethod
    def _merge_reranker_backward_compat(cls, value: object, info) -> RerankerProviderType:
        """老配置只设 reranker_provider='local|disabled' 时自动映射到新字段。"""
        if value:
            return RerankerProviderType(value)
        # 老字段优先兜底：如果在上下文中拿到 reranker_provider（field_validator 还读不到其它字段，
        # 所以在下面的 model_validator 里再处理一次）
        return RerankerProviderType.RERANKER_PROVIDER_TYPE

    @model_validator(mode="after")
    def _apply_provider_presets_and_fallbacks(self) -> "Settings":
        """按 provider_type 预设注入 base_url/model/dimension；保证向后兼容。

        覆盖规则（以 LLM 为例，Embedding 同理）：
          1. 用户显式设置了 LLM_BASE_URL → 用用户的 base_url；否则从预设读
          2. 用户显式设置了 LLM_MODEL → 用用户的 model；否则从预设读
          3. 用户显式设置了 EMBEDDING_DIMENSION → 用用户的；否则从预设读
        """
        # ----- reranker backward compat -----
        if (
            self.reranker_provider_type == RerankerProviderType.RERANKER_PROVIDER_TYPE
            and self.reranker_provider
        ):
            # 老变量兜底：只支持 local/disabled（与旧枚举一致）
            mapping = {
                "local": RerankerProviderType.RERANKER_PROVIDER_TYPE,
                "disabled": RerankerProviderType.DISABLED,
            }
            self.reranker_provider_type = mapping[self.reranker_provider]

        # ----- LLM preset -----
        llm_preset = _LLM_PRESETS[self.llm_provider_type]
        if not self.llm_base_url:
            self.llm_base_url = llm_preset["base_url"]
        if not self.llm_model and self.llm_provider_type != LlmProviderType.OPENAI_COMPATIBLE:
            # OPENAI_COMPATIBLE 通用兜底不预设 model，必须用户显式传
            self.llm_model = llm_preset["model"]

        # ----- Embedding preset -----
        emb_preset = _EMBEDDING_PRESETS[self.embedding_provider_type]
        if not self.embedding_base_url:
            self.embedding_base_url = str(emb_preset["base_url"])
        if not self.embedding_model and self.embedding_provider_type != EmbeddingProviderType.OPENAI_COMPATIBLE:
            self.embedding_model = str(emb_preset["model"])
        if self.embedding_dimension == 0:
            self.embedding_dimension = int(emb_preset["dimension"])

        return self

    @field_validator("embedding_dimension")
    @classmethod
    def _validate_dimension(cls, v: int) -> int:
        """只在最终值（预设注入后）被 pydantic 调用会失败；改为 model_validator 中注入后
        还需再手动校验。这里只限定显式传值的合理范围。"""
        if v != 0 and (v < 64 or v > 4096):
            raise ValueError("embedding_dimension must be 0 (use preset) or between 64 and 4096")
        return v

    # =========================================================================
    # 诊断属性（供健康检查与日志输出）
    # =========================================================================

    @property
    def phase(self) -> str:
        if self.llm_provider_type == LlmProviderType.NOOP:
            return "noop"
        return "minimal"

    def describe_providers(self) -> dict[str, str]:
        """返回可脱敏打印的 provider 摘要（不含密钥）。"""
        return {
            "llm": (
                f"{self.llm_provider_type.value}|model={self.llm_model}"
                f"|base_url={self._mask_url(self.llm_base_url)}"
            ),
            "embedding": (
                f"{self.embedding_provider_type.value}|model={self.embedding_model}"
                f"|dim={self.embedding_dimension}"
                f"|base_url={self._mask_url(self.embedding_base_url)}"
            ),
            "reranker": self.reranker_provider_type.value,
            "safety": self.safety_provider_type.value,
            "object_store": "minio" if (self.minio_access_key and self.minio_secret_key) else "disabled",
            "search_index": "pgvector" if (self.pgvector_enabled and self.database_url) else "disabled",
        }

    @staticmethod
    def _mask_url(url: str) -> str:
        """URL 中如果含 user:pass 形式，脱敏后输出。"""
        if not url:
            return "(empty)"
        if "@" in url:
            scheme, rest = url.split("://", 1) if "://" in url else ("", url)
            if "@" in rest:
                _, tail = rest.split("@", 1)
                return f"{scheme}://***:***@{tail}" if scheme else f"***:***@{tail}"
        return url


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """加载并缓存进程级配置。

    ``RAG_ENGINE_ENV_FILE`` 只负责选择配置文件，不进入 Settings 字段。
    未设置时读取当前工作目录的 ``.env``。
    """
    env_file = os.getenv("RAG_ENGINE_ENV_FILE")
    return Settings(_env_file=Path(env_file) if env_file else Path(".env"))


def clear_settings_cache() -> None:
    """清除设置缓存，供测试或受控的进程内重新装配使用。"""
    get_settings.cache_clear()
