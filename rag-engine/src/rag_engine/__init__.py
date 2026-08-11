"""rag-engine — 检索/解析/精排/生成引擎。

当前提供 v0.1 内部契约的最小联调实现。生产边界仍是“非公开 API，只接受工作负载
身份 + server 签名的短期 RetrievalAccessContext”；相关字段和校验尚待 v0.2
OpenAPI 冻结后实现，当前版本不得直接暴露到不可信网络。
"""

__version__ = "0.2.0"

__all__ = ["__version__"]
