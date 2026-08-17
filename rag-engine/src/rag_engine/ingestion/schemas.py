"""文档摄取 v0.1 HTTP DTO。"""

from pydantic import Field

from rag_engine.common.api import ApiModel, KbConfig
from rag_engine.ingestion.models import IngestStage, TaskStatus


class IngestRequest(ApiModel):
    """提交文档摄取任务的请求。"""

    document_id: int = Field(gt=0)
    object_key: str = Field(min_length=1)
    kb_config: KbConfig
    tenant_id: int = Field(gt=0)
    kb_id: int | None = Field(default=None, gt=0)
    version_no: int = Field(default=1, ge=1)
    # 2026-08-17 新增：document_version.id，chunk_meta 外键引用版本。
    version_id: int = Field(gt=0)


class IngestAccepted(ApiModel):
    """异步摄取任务受理结果。"""

    task_id: str


class IngestTaskStatus(ApiModel):
    """摄取任务状态响应。"""

    task_id: str
    stage: IngestStage
    status: TaskStatus
    vector_count: int = Field(default=0, ge=0)
    error_msg: str | None = None


class DeleteRequest(ApiModel):
    """按文档和可选版本幂等删除派生向量。"""

    document_id: int = Field(gt=0)
    version_no: int | None = Field(default=None, ge=1)


class DeleteResult(ApiModel):
    """索引删除计数。"""

    deleted_count: int = Field(ge=0)
