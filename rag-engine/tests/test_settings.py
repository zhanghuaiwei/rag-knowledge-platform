"""环境变量加载和配置装配测试。"""

from pathlib import Path

from fastapi.testclient import TestClient

from rag_engine.config import Environment, Settings, clear_settings_cache, get_settings
from rag_engine.main import create_app


def test_prefixed_environment_variables_override_defaults(monkeypatch) -> None:
    monkeypatch.setenv("RAG_ENGINE_ENVIRONMENT", "test")
    monkeypatch.setenv("RAG_ENGINE_PORT", "9010")
    monkeypatch.setenv("RAG_ENGINE_DOCS_ENABLED", "false")
    monkeypatch.setenv("RAG_ENGINE_MAX_IN_MEMORY_TASKS", "7")
    monkeypatch.setenv("RAG_ENGINE_RERANKER_PROVIDER", "disabled")

    settings = Settings(_env_file=None)

    assert settings.environment is Environment.TEST
    assert settings.port == 9010
    assert settings.docs_enabled is False
    assert settings.max_in_memory_tasks == 7
    assert settings.reranker_provider == "disabled"


def test_explicit_environment_file_is_loaded(monkeypatch, tmp_path: Path) -> None:
    env_file = tmp_path / "rag-engine.env"
    env_file.write_text(
        "RAG_ENGINE_SERVICE_NAME=rag-engine-test\nRAG_ENGINE_PORT=9020\n",
        encoding="utf-8",
    )
    monkeypatch.setenv("RAG_ENGINE_ENV_FILE", str(env_file))
    clear_settings_cache()

    try:
        settings = get_settings()
        assert settings.service_name == "rag-engine-test"
        assert settings.port == 9020
    finally:
        clear_settings_cache()


def test_settings_are_applied_to_fastapi_and_task_capacity() -> None:
    settings = Settings(
        _env_file=None,
        service_name="configured-rag-engine",
        docs_enabled=False,
        max_in_memory_tasks=1,
        reranker_provider="disabled",
    )
    client = TestClient(create_app(settings))

    assert client.get("/healthz").json()["service"] == "configured-rag-engine"
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404
    assert client.get("/api/engine/health").json()["models"][0] == {
        "name": "minimal-lexical-reranker",
        "available": False,
    }

    first_id = _submit_document(client, 1)
    _submit_document(client, 2)
    assert client.get(f"/api/ingest/tasks/{first_id}").status_code == 404


def _submit_document(client: TestClient, document_id: int) -> str:
    """提交满足 v0.1 契约的最小摄取请求并返回 taskId。"""
    response = client.post(
        "/api/ingest/documents",
        json={
            "documentId": document_id,
            "objectKey": f"quarantine/document-{document_id}",
            "kbConfig": {},
            "tenantId": 1,
        },
    )
    assert response.status_code == 202
    return response.json()["taskId"]
