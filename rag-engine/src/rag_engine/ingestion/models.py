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
    """不保存 objectKey 或正文的开发态任务快照。"""

    task_id: str
    stage: IngestStage
    status: TaskStatus
    vector_count: int = 0
    error_msg: str | None = None
