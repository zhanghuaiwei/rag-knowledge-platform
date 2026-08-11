"""文档摄取、任务查询和索引删除 HTTP 路由。"""

from typing import Annotated

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Path, status

from rag_engine.api.dependencies import get_ingestion_service
from rag_engine.ingestion.schemas import (
    DeleteRequest,
    DeleteResult,
    IngestAccepted,
    IngestRequest,
    IngestTaskStatus,
)
from rag_engine.ingestion.service import IngestionService

router = APIRouter(prefix="/api/ingest", tags=["ingest"])
Ingestion = Annotated[IngestionService, Depends(get_ingestion_service)]


@router.post("/documents", response_model=IngestAccepted, status_code=status.HTTP_202_ACCEPTED)
def submit_document(
    request: IngestRequest,
    background_tasks: BackgroundTasks,
    service: Ingestion,
) -> IngestAccepted:
    """受理摄取任务，并在响应后推进当前最小流水线。"""
    task_id = service.submit_document(
        tenant_id=request.tenant_id,
        document_id=request.document_id,
        version_no=request.version_no,
        object_key=request.object_key,
        kb_id=request.kb_id,
        kb_config=request.kb_config.model_dump(),
    )
    background_tasks.add_task(service.process_document, task_id)
    return IngestAccepted(task_id=task_id)


@router.get("/tasks/{id}", response_model=IngestTaskStatus)
def get_task(
    task_id: Annotated[str, Path(alias="id")],
    service: Ingestion,
) -> IngestTaskStatus:
    """查询任务；未知或已被内存仓库淘汰的任务返回 404。"""
    snapshot = service.get_task(task_id)
    if snapshot is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="ingest task not found")
    return IngestTaskStatus(
        task_id=snapshot.task_id,
        stage=snapshot.stage,
        status=snapshot.status,
        vector_count=snapshot.vector_count,
        error_msg=snapshot.error_msg,
    )


@router.post("/delete", response_model=DeleteResult)
def delete_document_vectors(request: DeleteRequest, service: Ingestion) -> DeleteResult:
    """幂等删除指定文档的派生向量。"""
    return DeleteResult(
        deleted_count=service.delete_vectors(
            document_id=request.document_id,
            version_no=request.version_no,
        )
    )
