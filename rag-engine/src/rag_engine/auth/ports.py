"""生产工作负载身份与授权上下文验证端口。"""

from collections.abc import Mapping
from typing import Protocol

from rag_engine.auth.models import RetrievalAccessContext, ServiceIdentity


class WorkloadAuthenticator(Protocol):
    """从可信传输元数据验证调用方工作负载身份。"""

    def authenticate(self, headers: Mapping[str, str]) -> ServiceIdentity:
        """验证请求头或 mTLS 属性，失败时必须拒绝请求。"""
        ...


class RetrievalAccessContextVerifier(Protocol):
    """验证签名、受众、时效、策略版本和防重放字段。"""

    def verify(
        self,
        token: str,
        *,
        identity: ServiceIdentity,
    ) -> RetrievalAccessContext:
        """返回已验证上下文；任何缺失或校验失败都必须 fail-closed。"""
        ...
