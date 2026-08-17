"""文档摄取任务的领域状态和不可变快照。"""

from dataclasses import dataclass
from enum import StrEnum


class IngestStage(StrEnum):
    """与 v0.1 OpenAPI 保持一致的摄取阶段。"""

    PARSING = "PARSING"
    SPLITTING = "SPLITTING"
    EMBEDDING = "EMBEDDING"
    DONE = "DONE"


class TaskStatus(StrEnum):
    """异步任务运行状态。"""

    RUNNING = "RUNNING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"


@dataclass(frozen=True, slots=True)
class IngestTaskSnapshot:
    """摄取任务快照。

    ⚠️ 2026-08-17：为支撑真实流水线，内存快照新增了 worker 需要的对象/版本上下文
    （object_key / document_id / version_id / kb_id / kb_config）。快照仅存进程内存，
    不落盘；重启后 404，符合 MVP「重启后允许重新摄取恢复」的约定。
    """

    task_id: str
    stage: IngestStage
    status: TaskStatus
    vector_count: int = 0
    error_msg: str | None = None
    object_key: str = ""
    document_id: int = 0
    version_id: int = 0
    kb_id: int | None = None
    tenant_id: int = 1
    kb_config: dict[str, object] | None = None
