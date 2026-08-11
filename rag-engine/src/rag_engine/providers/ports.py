"""不属于单一业务功能的基础设施端口。"""

from typing import Protocol


class ObjectStore(Protocol):
    """原始文档和派生预览的不可变对象存储端口。"""

    def get(self, object_key: str) -> bytes:
        """读取对象内容；实现必须配置超时并映射 provider 错误。"""
        ...

    def head(self, object_key: str) -> bool:
        """判断对象是否存在，不返回对象正文。"""
        ...


class SecretResolver(Protocol):
    """按 secret reference 获取短期凭证的端口。"""

    def resolve(self, secret_ref: str) -> str:
        """解析凭证；返回值不得落盘、记录日志或进入 API 响应。"""
        ...
