"""server 工作负载身份与检索授权上下文模型。"""

from __future__ import annotations

from datetime import UTC, datetime

from pydantic import BaseModel, Field


class ServiceIdentity(BaseModel):
    """完成 mTLS 或服务令牌校验后注入的调用方身份。"""

    service: str = Field(min_length=1)
    audience: str = Field(min_length=1)


class RetrievalAccessContext(BaseModel):
    """server 签发的短期、最小权限检索上下文。"""

    subject: str = Field(min_length=1)
    tenant_id: int = Field(gt=0)
    policy_version: int = Field(ge=1)
    allowed_document_ids: list[str] = Field(default_factory=list)
    signature: str = Field(min_length=1)
    expires_at: datetime

    def is_expired(self, *, now: datetime | None = None) -> bool:
        """按 UTC 判断上下文是否过期，便于测试注入确定性时间。"""
        current = now or datetime.now(UTC)
        return current > self.expires_at
