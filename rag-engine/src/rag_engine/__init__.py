"""rag-engine — 检索/解析/精排/生成引擎（v0.2 骨架）。

不是公开 API：只接受工作负载身份 + server 签名的短期 ``RetrievalAccessContext``
（02-概要设计 §2.1 信任边界）。业务路由见 ``03-详细设计 §8``。
"""
