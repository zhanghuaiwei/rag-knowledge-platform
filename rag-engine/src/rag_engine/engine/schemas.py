"""引擎能力健康与模型路由 v0.1 HTTP DTO。"""

from enum import StrEnum

from pydantic import Field

from rag_engine.common.api import ApiModel


class EngineModel(ApiModel):
    """单项模型或 provider 能力状态。"""

    name: str
    available: bool


class EngineHealth(ApiModel):
    """详细引擎能力健康响应。"""

    status: str = Field(pattern="^(ok|degraded|down)$")
    models: list[EngineModel]


class RouteType(StrEnum):
    """v0.1 支持探测的模型路由类型。"""

    EMBEDDING = "embedding"
    LLM = "llm"


class RouteStatusRequest(ApiModel):
    """指定模型路由探测请求。"""

    route_type: RouteType
    model_name: str = Field(min_length=1)


class RouteStatus(ApiModel):
    """模型路由可用性与缓存探测延迟。"""

    available: bool
    latency_ms: int = Field(ge=0)
