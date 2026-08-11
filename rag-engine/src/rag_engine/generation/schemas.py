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
