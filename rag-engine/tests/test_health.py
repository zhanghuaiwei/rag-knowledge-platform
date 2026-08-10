"""脚手架阶段冒烟测试：确认 FastAPI 应用可构建、探针可访问。"""

from fastapi.testclient import TestClient

from rag_engine.main import app


def test_healthz() -> None:
    client = TestClient(app)
    resp = client.get("/healthz")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["service"] == "rag-engine"
