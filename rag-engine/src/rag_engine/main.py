"""rag-engine FastAPI 入口（v0.2 骨架）。

当前仅提供健康检查，用于验证进程可启动、探针可工作。
检索/解析/生成等真实端点必须在 server→rag-engine 内部契约
（RetrievalAccessContext / 服务身份认证）冻结后实现，路由挂载在
``rag_engine/api/routers/``。
"""
from fastapi import FastAPI

from rag_engine.api.routers.health import router as health_router

app = FastAPI(title="ragkb-rag-engine", version="0.2.0-SNAPSHOT")
app.include_router(health_router)
