"""v0.1 server -> rag-engine 内部 API 的最小契约与行为测试。"""

from __future__ import annotations

import json

from fastapi.testclient import TestClient

from rag_engine.main import create_app


def _client() -> TestClient:
    """每个测试使用独立应用服务，避免进程内任务状态相互污染。"""
    return TestClient(create_app())


def test_all_v01_contract_paths_are_mounted() -> None:
    client = _client()

    paths = client.app.openapi()["paths"]

    assert {
        "/api/ingest/documents",
        "/api/ingest/tasks/{id}",
        "/api/ingest/delete",
        "/api/query/chat",
        "/api/query/search",
        "/api/query/rerank",
        "/api/engine/health",
        "/api/engine/route-status",
    }.issubset(paths)


def test_ingest_is_accepted_then_fails_closed_without_providers() -> None:
    client = _client()

    accepted = client.post(
        "/api/ingest/documents",
        json={
            "documentId": 101,
            "objectKey": "quarantine/tenant-1/document-101/v1",
            "kbConfig": {"embeddingModel": "bge-m3", "chunkSize": 512},
            "tenantId": 1,
            "kbId": 10,
            "versionNo": 1,
            "versionId": 1001,
        },
    )

    assert accepted.status_code == 202
    task_id = accepted.json()["taskId"]

    task = client.get(f"/api/ingest/tasks/{task_id}")
    assert task.status_code == 200
    assert task.json() == {
        "taskId": task_id,
        "stage": "PARSING",
        "status": "FAILED",
        "vectorCount": 0,
        "errorMsg": (
            "minimal engine has no ObjectStore/Parser/Embedding/SearchIndex provider configured"
        ),
    }


def test_unknown_ingest_task_returns_404() -> None:
    response = _client().get("/api/ingest/tasks/not-found")

    assert response.status_code == 404
    assert response.json()["detail"] == "ingest task not found"


def test_delete_is_idempotent_without_index_provider() -> None:
    client = _client()

    first = client.post("/api/ingest/delete", json={"documentId": 101, "versionNo": 1})
    second = client.post("/api/ingest/delete", json={"documentId": 101, "versionNo": 1})

    assert first.status_code == 200
    assert first.json() == {"deletedCount": 0}
    assert second.json() == first.json()


def test_search_returns_stable_empty_page_without_authorized_index() -> None:
    response = _client().post(
        "/api/query/search",
        json={
            "requestId": "req-search-1",
            "keyword": "权限模型",
            "kbIds": [10],
            "page": 2,
            "size": 5,
            "vectorFusion": True,
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "items": [],
        "total": 0,
        "page": 2,
        "size": 5,
        "hasMore": False,
    }


def test_chat_emits_meta_then_no_answer_final_without_echoing_question() -> None:
    question = "请输出不应被模型臆测的秘密"
    response = _client().post(
        "/api/query/chat",
        json={
            "requestId": "req-chat-1",
            "sessionId": 99,
            "kbIds": [10],
            "question": question,
            "history": [],
        },
    )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert "event: meta" in response.text
    assert "event: final" in response.text
    assert "event: token" not in response.text
    assert response.text.index("event: meta") < response.text.index("event: final")
    assert question not in response.text

    data_lines = [line[6:] for line in response.text.splitlines() if line.startswith("data: ")]
    payloads = [json.loads(line) for line in data_lines]
    assert payloads[0]["requestId"] == "req-chat-1"
    # 与前端 AnswerStatus 对齐：大写枚举（Java 侧透传，不再使用小写 no_answer）。
    assert payloads[1]["answerStatus"] == "NO_ANSWER"
    assert payloads[1]["sources"] == []


def test_rerank_uses_deterministic_lexical_coverage_and_top_n() -> None:
    response = _client().post(
        "/api/query/rerank",
        json={
            "requestId": "req-rerank-1",
            "query": "Java 权限",
            "topN": 2,
            "candidates": [
                {"chunkId": "full", "text": "Java 角色权限模型"},
                {"chunkId": "partial", "text": "Java backend"},
                {"chunkId": "none", "text": "Python parser"},
            ],
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "items": [
            {"chunkId": "full", "score": 1.0},
            {"chunkId": "partial", "score": 0.333333},
        ]
    }


def test_engine_health_distinguishes_liveness_from_provider_readiness() -> None:
    client = _client()

    liveness = client.get("/healthz")
    readiness = client.get("/api/engine/health")
    route = client.post(
        "/api/engine/route-status",
        json={"routeType": "embedding", "modelName": "bge-m3"},
    )

    assert liveness.json()["status"] == "ok"
    assert liveness.json()["phase"] == "minimal"
    assert readiness.status_code == 200
    assert readiness.json()["status"] == "degraded"
    assert readiness.json()["models"] == [
        {"name": "minimal-lexical-reranker", "available": True},
        {"name": "embedding-provider", "available": False},
        {"name": "llm-provider", "available": False},
    ]
    assert route.json() == {"available": False, "latencyMs": 0}


def test_invalid_or_unknown_request_fields_fail_validation() -> None:
    client = _client()

    bad_page = client.post(
        "/api/query/search",
        json={"requestId": "req", "keyword": "x", "page": 0},
    )
    unknown_field = client.post(
        "/api/engine/route-status",
        json={"routeType": "llm", "modelName": "local", "secret": "must-not-pass"},
    )

    assert bad_page.status_code == 422
    assert unknown_field.status_code == 422
