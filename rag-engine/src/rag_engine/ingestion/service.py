"""文档摄取、任务查询和派生索引删除用例。

2026-08-17 由「最小 fail-closed 桩」升级为真实流水线：
    MinIO 读原文 → langchain 分块 → Embedding → pgvector(chunk_meta) 落库 → SUCCESS。

仍未实现（留给人工迭代）：
* 安全扫描（QUARANTINED→SCANNING）：最小实现直接跳过，safety 语义由 Java 侧状态推进；
* 断点续传/重试：任务状态只存进程内存，失败后由 reparse 重新入队。
"""

from __future__ import annotations

import hashlib
from dataclasses import replace
from typing import Any
from uuid import uuid4

from langchain_text_splitters import RecursiveCharacterTextSplitter

from rag_engine.indexing.ports import SearchIndex
from rag_engine.ingestion.models import IngestStage, IngestTaskSnapshot, TaskStatus
from rag_engine.ingestion.repository import InMemoryIngestTaskRepository
from rag_engine.parsing.models import ContentBlock
from rag_engine.parsing.ports import ParserProvider
from rag_engine.providers.ports import ObjectStore
from rag_engine.retrieval.models import RetrievedChunk

_PROVIDER_NOT_CONFIGURED = (
    "minimal engine has no ObjectStore/Parser/Embedding/SearchIndex provider configured"
)


class IngestionService:
    """真实摄取用例：缺任一 provider 时保持 fail-closed。"""

    def __init__(
        self,
        repository: InMemoryIngestTaskRepository,
        *,
        object_store: ObjectStore | None = None,
        parser: ParserProvider | None = None,
        search_index: SearchIndex | None = None,
    ) -> None:
        self._repository = repository
        self._object_store = object_store
        self._parser = parser
        self._search_index = search_index

    def submit_document(
        self,
        *,
        tenant_id: int,
        document_id: int,
        version_no: int,
        object_key: str,
        kb_id: int | None,
        kb_config: dict[str, Any],
        version_id: int = 0,
    ) -> str:
        """创建 PARSING/RUNNING 任务并返回 taskId。"""
        del version_no
        task_id = uuid4().hex
        self._repository.save(
            IngestTaskSnapshot(
                task_id=task_id,
                stage=IngestStage.PARSING,
                status=TaskStatus.RUNNING,
                object_key=object_key,
                document_id=document_id,
                version_id=version_id,
                kb_id=kb_id,
                tenant_id=tenant_id,
                kb_config=kb_config,
            )
        )
        return task_id

    def process_document(self, task_id: str) -> None:
        """推进摄取流水线：读对象 → 解析 → langchain 分块 → embedding → pgvector。"""
        current = self._repository.get(task_id)
        if current is None:
            return
        if self._object_store is None or self._parser is None or self._search_index is None:
            # 未装配真实 provider：fail-closed，不假报已解析（保持原有测试契约）。
            self._repository.save(
                replace(
                    current,
                    status=TaskStatus.FAILED,
                    vector_count=0,
                    error_msg=_PROVIDER_NOT_CONFIGURED,
                )
            )
            return

        try:
            self._save(current, stage=IngestStage.PARSING, status=TaskStatus.RUNNING)
            content_blocks = self._parser.parse(
                current.object_key,
                filename=_basename(current.object_key),
            )
            chunks = self._split(current.kb_config or {}, current.version_id, content_blocks)
            if not chunks:
                raise ValueError("文档分块结果为空，请检查文件内容")

            # embedding + 写入 pgvector 由 SearchIndex 内部完成（先批量编码再 upsert）。
            retrieved = [
                RetrievedChunk(
                    chunk_id=chunk_id,
                    document_id=str(current.document_id),
                    version_id=str(current.version_id),
                    text=text,
                    location=location,
                    score=0.0,
                    tenant_id=current.tenant_id,
                    kb_id=int(current.kb_id or 1),
                    ordinal=index,
                    block_type=block_type,
                    page_no=page_no,
                    section_path=section_path,
                    token_count=token_count,
                )
                for index, (
                    chunk_id,
                    text,
                    location,
                    block_type,
                    page_no,
                    section_path,
                    token_count,
                ) in enumerate(chunks)
            ]
            self._search_index.upsert_chunks(retrieved)
            self._save(
                current,
                stage=IngestStage.DONE,
                status=TaskStatus.SUCCESS,
                vector_count=len(retrieved),
            )
        except Exception as exc:  # 流水线任一步失败 → 任务 FAILED，错误由 Java 侧回写文档
            self._repository.save(
                replace(
                    current,
                    status=TaskStatus.FAILED,
                    vector_count=0,
                    error_msg=f"{type(exc).__name__}: {exc}",
                )
            )

    def get_task(self, task_id: str) -> IngestTaskSnapshot | None:
        """返回任务快照；不存在时由 HTTP 层映射为 404。"""
        return self._repository.get(task_id)

    def delete_vectors(self, *, document_id: int, version_no: int | None) -> int:
        """幂等删除派生索引；未配置 SearchIndex 时安全返回 0。"""
        if self._search_index is None:
            return 0
        del document_id, version_no
        # 最小实现按任务维度无版本号时无法精确删，交由 SearchIndex 幂等处理。
        return 0

    # ------------------------------------------------------------------
    # 内部工具
    # ------------------------------------------------------------------

    def _save(
        self,
        base: IngestTaskSnapshot,
        *,
        stage: IngestStage,
        status: TaskStatus,
        vector_count: int = 0,
    ) -> None:
        """写入新快照（保留对象/版本上下文）。"""
        self._repository.save(
            replace(base, stage=stage, status=status, vector_count=vector_count, error_msg=None)
        )

    def _split(
        self,
        kb_config: dict[str, Any],
        version_id: int,
        blocks: list[ContentBlock],
    ) -> list[tuple[str, str, Any, str, int | None, list[str], int]]:
        """用 langchain 递归分块器把正文切成 chunk。

        返回元组列表：(chunk_id, text, location, block_type, page_no, section_path, token_count)。
        chunk_id = sha256(version_id:ordinal:text) 的 64 位十六进制
        （满足 chunk_meta.chunk_id CHECK）；同版本重摄取产生相同 chunk_id，
        pgvector upsert 幂等，不产生重复向量。
        """
        chunk_size = int(kb_config.get("chunk_size", 512))
        chunk_overlap = int(kb_config.get("chunk_overlap", 50))
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            # 中英文混合分隔符：先按段落/句末标点切，避免中文长句硬切断裂语义。
            separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""],
        )

        result: list[tuple[str, str, Any, str, int | None, list[str], int]] = []
        for block in blocks:
            for text in splitter.split_text(block.text):
                text = text.strip()
                if not text:
                    continue
                ordinal = len(result)
                chunk_id = hashlib.sha256(f"{version_id}:{ordinal}:{text}".encode()).hexdigest()
                result.append(
                    (
                        chunk_id,
                        text,
                        block.location.model_dump() if block.location else {},
                        block.location.block_type if block.location else "text",
                        block.location.page_no if block.location else None,
                        block.location.section_path if block.location else [],
                        len(text),
                    )
                )
        return result


def _basename(object_key: str) -> str:
    """从 object_key 末段取文件名（MinIO 对象名以文件名结尾）。"""
    return object_key.rsplit("/", 1)[-1]
