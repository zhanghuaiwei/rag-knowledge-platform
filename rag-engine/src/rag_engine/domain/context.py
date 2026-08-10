"""信任边界模型：调用方服务身份与 server 签发的检索授权上下文。

rag-engine 不是公开 API：只接受工作负载身份 + server 签名的短期
``RetrievalAccessContext``。缺失/过期/签名无效即拒绝（fail-closed）。
"""
from __future__ import annotations

from datetime import UTC, datetime

from pydantic import BaseModel, Field


class ServiceIdentity(BaseModel):
    """调用方工作负载身份（mTLS / service token 校验后注入）。"""

    service: str
    audience: str


class RetrievalAccessContext(BaseModel):
    """server 签发的短期检索授权上下文。

    rag-engine 校验 ``signature`` 与 ``expires_at`` 后，仅按
    ``allowed_document_ids`` 执行授权检索；缺失/过期/签名无效即拒绝。
    """

    subject: str
    tenant_id: int = Field(gt=0)
    policy_version: int = Field(ge=1)
    allowed_document_ids: list[str] = Field(default_factory=list)
    signature: str
    expires_at: datetime

    def is_expired(self) -> bool:
        """是否已过期（UTC 比较）。"""
        return datetime.now(UTC) > self.expires_at
