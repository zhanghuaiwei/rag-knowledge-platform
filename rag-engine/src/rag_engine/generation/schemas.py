"""智能问答 v0.1 HTTP DTO。"""

from pydantic import Field

from rag_engine.common.api import ApiModel, KbConfig


class ChatHistoryItem(ApiModel):
    """问答历史中的单条消息。"""

    role: str = Field(pattern="^(user|assistant)$")
    content: str = Field(min_length=1)


class QueryChatRequest(ApiModel):
    """混合检索问答请求。"""

    request_id: str = Field(min_length=1)
    session_id: int | None = Field(default=None, gt=0)
    kb_ids: list[int]
    question: str = Field(min_length=1)
    history: list[ChatHistoryItem] = Field(default_factory=list)
    kb_config: KbConfig = Field(default_factory=KbConfig)
    # 2026-08-17 新增：租户过滤（Java 侧从会话解析后透传；默认租户 1 兼容单租户开发）。
    tenant_id: int = Field(default=1, gt=0)
