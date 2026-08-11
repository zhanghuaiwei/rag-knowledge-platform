"""文档摄取、任务查询和派生索引删除用例。"""

from __future__ import annotations

from dataclasses import replace
from typing import Any
from uuid import uuid4

from rag_engine.ingestion.models import IngestStage, IngestTaskSnapshot, TaskStatus
from rag_engine.ingestion.repository import InMemoryIngestTaskRepository

_PROVIDER_NOT_CONFIGURED = (
    "minimal engine has no ObjectStore/Parser/Embedding/SearchIndex provider configured"
)


class IngestionService:
    """无外部 provider 时显式失败的最小摄取用例。"""

    def __init__(self, repository: InMemoryIngestTaskRepository) -> None:
        self._repository = repository

    def submit_document(
        self,
        *,
        tenant_id: int,
        document_id: int,
        version_no: int,
        object_key: str,
        kb_id: int | None,
        kb_config: dict[str, Any],
    ) -> str:
        """创建 RUNNING 任务并返回 taskId。

        TODO(IngestionService.submit_document): v0.2 契约加入幂等键后接入持久化
        task/outbox，并只向 worker 传递安全扫描通过的 object reference。
        """
        # 最小实现只验证参数已经到达用例，不留存对象位置或租户配置等敏感元数据。
        del tenant_id, document_id, version_no, object_key, kb_id, kb_config
        task_id = uuid4().hex
        self._repository.save(
            IngestTaskSnapshot(
                task_id=task_id,
                stage=IngestStage.PARSING,
                status=TaskStatus.RUNNING,
            )
        )
        return task_id

    def process_document(self, task_id: str) -> None:
        """推进摄取流水线；未装配 provider 时 fail-closed。

        TODO(IngestionService.process_document): 按 safety -> parse -> split -> embed ->
        index 调用功能端口，并逐阶段持久化可重试状态。
        """
        current = self._repository.get(task_id)
        if current is None:
            return
        self._repository.save(
            replace(
                current,
                status=TaskStatus.FAILED,
                vector_count=0,
                error_msg=_PROVIDER_NOT_CONFIGURED,
            )
        )

    def get_task(self, task_id: str) -> IngestTaskSnapshot | None:
        """返回任务快照；不存在时由 HTTP 层映射为 404。"""
        return self._repository.get(task_id)

    def delete_vectors(self, *, document_id: int, version_no: int | None) -> int:
        """幂等删除派生索引；未配置 SearchIndex 时安全返回 0。

        TODO(IngestionService.delete_vectors): v0.2 DeleteRequest 带 tenantId 后调用
        SearchIndex.delete_by_version，并产出可审计的分索引删除结果。
        """
        del document_id, version_no
        return 0
