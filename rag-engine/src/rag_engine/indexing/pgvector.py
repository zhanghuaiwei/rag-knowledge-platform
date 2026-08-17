"""基于 PostgreSQL pgvector 的检索索引（chunk_meta.embedding）。

设计要点：
* 向量直接落在 ``chunk_meta`` 表（对齐 DDL），不引入 langchain 自带向量库；
  ``embedding vector(1024)`` 由 deploy/ddl/migrations/V0.6 迁移新增。
* 摄取时**先按批一次性编码全部 chunk 文本**（避免逐 chunk 调 Embedding API），
  再批量 upsert；按 chunk_id 幂等，重复摄取不产生重复行。
* 检索走 HNSW 余弦索引 ``ORDER BY embedding <=> ?::vector``，取 top_k 后按
  ``1 - distance`` 转相似度，再过滤低于 min_score 的「无证据」候选。
* 每个 SQL 均按 tenant_id + kb_id/document_id 过滤，杜绝跨租户串数据
  （查询侧最小授权：只返回授权上下文允许的文档）。
"""

from __future__ import annotations

import hashlib
import json
from typing import Any

from psycopg import sql as psql
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from rag_engine.indexing.ports import SearchIndex
from rag_engine.providers.embeddings import OpenAiCompatibleEmbedding
from rag_engine.retrieval.models import RetrievedChunk, SearchQuery, SearchResult


class PgVectorSearchIndex(SearchIndex):
    """SearchIndex 端口的 pgvector 实现（摄取写、问答查）。"""

    def __init__(
        self,
        *,
        database_url: str,
        embedding: OpenAiCompatibleEmbedding,
        embedding_model: str,
        min_score: float = 0.30,
        embedding_batch_size: int = 64,
    ) -> None:
        self._pool = ConnectionPool(conninfo=database_url, min_size=1, max_size=4, open=False)
        self._pool.open()
        self._embedding = embedding
        self._embedding_model = embedding_model
        self._min_score = min_score
        if embedding_batch_size < 1:
            raise ValueError("embedding_batch_size must be positive")
        self._embedding_batch_size_setting = embedding_batch_size

    # ------------------------------------------------------------------
    # 摄取写入
    # ------------------------------------------------------------------

    def upsert_chunks(self, chunks: list[RetrievedChunk]) -> None:
        """按 chunk_id 幂等写入 chunk_meta（含 embedding 向量）。

        先对全部 chunk 文本按批编码（一次 Embedding 往返），再逐行 upsert。
        index_profile_id / policy_version 从 kb 表解析（kb.index_profile_id 为空时
        回退到该租户第一个 index_profile），保证 chunk_meta 外键完整性。
        """
        if not chunks:
            return
        vectors = self._embed_vectors([chunk.text for chunk in chunks])
        kb_configs = self._load_kb_configs(chunks)
        with self._pool.connection() as conn, conn.cursor() as cur:
            for chunk, vector in zip(chunks, vectors, strict=True):
                profile_id, policy_version = kb_configs[chunk.kb_id]
                cur.execute(
                    """
                    INSERT INTO chunk_meta (
                        chunk_id, tenant_id, kb_id, document_id, version_id,
                        index_profile_id, ordinal, block_type, page_no,
                        section_path, location_json, chunk_text, text_sha256,
                        token_count, policy_version, embedding
                    ) VALUES (
                        %s, %s, %s, %s, %s,
                        %s, %s, %s, %s,
                        %s::jsonb, %s::jsonb, %s, %s,
                        %s, %s, %s::vector
                    )
                    ON CONFLICT (chunk_id) DO UPDATE SET
                        embedding = EXCLUDED.embedding,
                        chunk_text = EXCLUDED.chunk_text,
                        text_sha256 = EXCLUDED.text_sha256,
                        token_count = EXCLUDED.token_count
                    """,
                    (
                        chunk.chunk_id,
                        chunk.tenant_id,
                        chunk.kb_id,
                        int(chunk.document_id),
                        int(chunk.version_id),
                        profile_id,
                        chunk.ordinal,
                        chunk.block_type,
                        chunk.page_no,
                        json.dumps(chunk.section_path, ensure_ascii=False),
                        json.dumps(chunk.location if chunk.location is not None else {}),
                        chunk.text,
                        self._sha256(chunk.text),
                        chunk.token_count,
                        policy_version,
                        _to_vector_literal(vector),
                    ),
                )

    def delete_by_version(self, version_id: str) -> int:
        """删除指定文档版本的全部向量，返回删除条数（幂等）。"""
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                "DELETE FROM chunk_meta WHERE version_id = %s RETURNING chunk_id",
                (int(version_id),),
            )
            return len(cur.fetchall())

    def switch_alias(self, alias: str, physical: str) -> None:
        """单表无别名切换；生产 multi-build 阶段再实现 alias 原子切换。"""
        del alias, physical

    # ------------------------------------------------------------------
    # 问答检索
    # ------------------------------------------------------------------

    def search(self, query: SearchQuery) -> SearchResult:
        """对问题编码 query embedding → 余弦 top_k → 相似度阈值过滤。

        allowed_document_ids 非空时按文档白名单过滤（Java 侧授权已收敛到文档集）。
        """
        query_vector = self._embedding.embed([query.question], model=self._embedding_model)[0]
        # 用 psycopg.sql 组合固定字面量 WHERE 子句（值全部参数绑定），无注入面。
        # 列名带 cm. 前缀，避免与 LEFT JOIN 的 document 表列名歧义。
        clauses = [psql.SQL("cm.tenant_id = %s")]
        params: list[Any] = [query.tenant_id]
        if query.kb_ids:
            clauses.append(psql.SQL("cm.kb_id = ANY(%s)"))
            params.append(list(query.kb_ids))
        if query.allowed_document_ids:
            clauses.append(psql.SQL("cm.document_id = ANY(%s)"))
            params.append([int(doc_id) for doc_id in query.allowed_document_ids])

        where = psql.SQL(" AND ").join(clauses)
        sql = psql.SQL(
            """
            SELECT cm.chunk_id, cm.document_id, cm.version_id, cm.chunk_text,
                   cm.page_no, cm.section_path, d.file_name,
                   1 - (cm.embedding <=> %s::vector) AS score
            FROM chunk_meta cm
            LEFT JOIN document d
                   ON d.tenant_id = cm.tenant_id AND d.id = cm.document_id
            WHERE {where}
            ORDER BY cm.embedding <=> %s::vector
            LIMIT %s
            """
        ).format(where=where)
        params += [query_vector, query_vector, query.top_k]

        hits: list[RetrievedChunk] = []
        with self._pool.connection() as conn, conn.cursor(row_factory=dict_row) as cur:
            cur.execute(sql, params)
            for row in cur.fetchall():
                score = float(row["score"])
                if score < self._min_score:
                    continue  # 低于阈值视为无证据，不进入生成
                hits.append(
                    RetrievedChunk(
                        chunk_id=row["chunk_id"],
                        document_id=str(row["document_id"]),
                        version_id=str(row["version_id"]),
                        text=row["chunk_text"],
                        location={
                            "page_no": row["page_no"],
                            "section_path": row["section_path"] or [],
                        },
                        score=score,
                        file_name=row["file_name"] or "",
                    )
                )
        return SearchResult(hits=hits, total=len(hits), policy_version=1)

    # ------------------------------------------------------------------
    # 内部工具
    # ------------------------------------------------------------------

    def _embed_vectors(self, texts: list[str]) -> list[list[float]]:
        """按批编码文本向量（embedding 调用一次拿到全部 chunk）。"""
        vectors: list[list[float]] = []
        for index in range(0, len(texts), self._embedding_batch_size()):
            batch = texts[index : index + self._embedding_batch_size()]
            vectors.extend(self._embedding.embed(batch, model=self._embedding_model))
        return vectors

    def _embedding_batch_size(self) -> int:
        """Embedding 批大小，与 Settings.embedding_batch_size 注入一致。"""
        return self._embedding_batch_size_setting

    def _load_kb_configs(self, chunks: list[RetrievedChunk]) -> dict[int, tuple[int, int]]:
        """按 kb_id 解析 index_profile_id 与 policy_version（一次查库缓存）。

        多租户下每个 kb 属于固定 tenant，查询条件必须带上 chunk 中的真实 tenant_id
        （不再硬编码 tenant=1 兜底），避免跨租户读到别的租户同名 kb 配置。
        """
        kb_ids = sorted({chunk.kb_id for chunk in chunks})
        kb_tenant_map: dict[int, int] = {chunk.kb_id: chunk.tenant_id for chunk in chunks}
        result: dict[int, tuple[int, int]] = {}
        with self._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                "SELECT id, tenant_id, index_profile_id, policy_version FROM kb WHERE id = ANY(%s)",
                (kb_ids,),
            )
            for kb_id, tenant_id, profile_id, policy_version in cur.fetchall():
                expected_tenant = kb_tenant_map.get(int(kb_id))
                if expected_tenant is not None and int(tenant_id) != expected_tenant:
                    raise ValueError(
                        f"kb {kb_id} tenant mismatch: chunk expects {expected_tenant}, "
                        f"db row has {tenant_id}"
                    )
                resolved_profile = int(profile_id) if profile_id is not None else 0
                resolved = (resolved_profile, int(policy_version or 1))
                result[int(kb_id)] = resolved
            # 缺 profile 的 kb：回退到该 kb 真实租户下第一个 index_profile。
            for kb_id, (profile_id, policy_version) in list(result.items()):
                if profile_id == 0:
                    fallback_tenant = kb_tenant_map.get(kb_id)
                    if fallback_tenant is None:
                        continue
                    cur.execute(
                        "SELECT id FROM index_profile WHERE tenant_id = %s ORDER BY id LIMIT 1",
                        (fallback_tenant,),
                    )
                    row = cur.fetchone()
                    if row:
                        result[kb_id] = (int(row[0]), policy_version)
        return result

    @staticmethod
    def _sha256(text: str) -> str:
        return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _to_vector_literal(vector: list[float]) -> str:
    """把归一化向量序列化为 pgvector 文本表示（'[0.1,0.2,...]'）作为 SQL 参数。"""
    return "[" + ",".join(f"{value:.8f}" for value in vector) + "]"
