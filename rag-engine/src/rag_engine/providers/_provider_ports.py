"""适配器层内部使用的端口定义。

注意：这些 Protocol 是 ``providers/`` 适配器实现（embeddings/llm/object_store）
与基础设施注册器（registry）之间的契约，**不应被业务域模块直接 import**。
业务域应通过各自 ``<domain>/ports.py`` 中定义的业务端口与基础设施解耦。

文件名加下划线前缀以区别于业务域的 ``ports.py``，避免 IDE 自动 import 选错。
"""

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
