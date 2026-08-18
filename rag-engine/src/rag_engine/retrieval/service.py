"""全文搜索应用用例。"""

from __future__ import annotations

from datetime import date
from typing import TYPE_CHECKING

from rag_engine.retrieval.models import FulltextQuery, FulltextRow, SearchPageSnapshot

if TYPE_CHECKING:
    from rag_engine.indexing.ports import SearchIndex
    from rag_engine.rerank.service import RerankService

# snippet 截断窗口：约 200 字符（前端卡片摘要可读长度，避免整段 chunk 直接下发）。
_SNIPPET_WINDOW = 200
# 融合模式召回放大倍数：向量窗口放大后按词项覆盖重排，再切分页窗口。
_FUSION_FETCH_MULTIPLIER = 3
# 融合分权重：向量相似度 0.6 + 词项覆盖率 0.4（确定性线性融合，便于回归验证）。
_VECTOR_WEIGHT = 0.6
_COVERAGE_WEIGHT = 0.4
# 前端展示量纲：score 期望 0-20 区间（相关度百分比 = score / 20 * 100）。
_SCORE_SCALE = 20.0


class RetrievalService:
    """全文搜索用例；未装配授权索引时返回稳定空结果（不泄露索引内容）。

    检索策略（2026-08-17 链路打通）：
    * 基线：关键词 query embedding → pgvector 余弦召回（tenant/kb/白名单/类型/时间过滤）；
    * ``vector_fusion`` 且本地精排可用时：召回窗口放大 3 倍 → 词项覆盖率精排 →
      线性融合（0.6 向量 + 0.4 覆盖）重排序 → offset 切片，保证关键词命中的
      片段优先于纯语义相近片段；
    * snippet 由本层生成纯文本（前端在客户端做 <mark> 高亮，服务端不做标记包裹）。
    """

    def __init__(
        self,
        *,
        search_index: SearchIndex | None = None,
        reranker: RerankService | None = None,
    ) -> None:
        self._search_index = search_index
        self._reranker = reranker

    def search(
        self,
        *,
        request_id: str,
        keyword: str,
        kb_ids: list[int],
        doc_id_whitelist: list[int],
        file_types: list[str],
        date_from: date | None,
        date_to: date | None,
        vector_fusion: bool,
        page: int,
        size: int,
        tenant_id: int = 1,
    ) -> SearchPageSnapshot:
        """返回分页命中；未装配索引时返回确定性空分页（fail-closed）。

        多租户红线：``tenant_id`` 贯穿索引查询；NoopEmbedding/零向量场景下
        余弦距离恒为 1（score=0），不崩溃、返回空结果。
        """
        del request_id  # 当前无请求级日志/幂等诉求，显式丢弃避免 lint 告警。
        if self._search_index is None:
            return SearchPageSnapshot(items=(), total=0, page=page, size=size, has_more=False)

        offset = (page - 1) * size
        # 融合模式仅在本地精排器可用时生效（不可用时退化为纯向量序，fail-closed 不报错）。
        fusion_enabled = vector_fusion and self._reranker is not None and self._reranker.available
        if fusion_enabled:
            # 一次取全量窗口（放大 3 倍并受 FulltextQuery.limit ≤ 300 上限保护），
            # 重排后在内存中切片；深翻页超出上限时自动退化为 SQL offset 直取。
            fetch_limit = min(size * _FUSION_FETCH_MULTIPLIER + offset, 300)
            fetch_offset = 0
        else:
            fetch_limit = size
            fetch_offset = offset

        rows, total = self._search_index.fulltext_search(
            FulltextQuery(
                keyword=keyword,
                tenant_id=tenant_id,
                kb_ids=kb_ids,
                doc_id_whitelist=doc_id_whitelist,
                file_types=file_types,
                date_from=date_from,
                date_to=date_to,
                limit=fetch_limit,
                offset=fetch_offset,
            )
        )

        window = rows
        reranker = self._reranker
        if fusion_enabled and rows and reranker is not None:
            # 词项覆盖率（0-1）：query 词项在候选文本中的命中比例（确定性本地精排）。
            coverage = {
                item.chunk_id: item.score
                for item in reranker.rerank(
                    query=keyword,
                    candidates=[(row.chunk_id, row.text) for row in rows],
                    top_n=len(rows),
                )
            }
            # 融合分降序；同分按 chunk_id 升序兜底，保证分页结果确定性可复现。
            rows = sorted(
                rows,
                key=lambda row: (
                    -(
                        _VECTOR_WEIGHT * row.score
                        + _COVERAGE_WEIGHT * coverage.get(row.chunk_id, 0.0)
                    ),
                    row.chunk_id,
                ),
            )
            # 重排后按请求页码在内存中切分页窗口。
            window = rows[offset : offset + size]

        has_more = offset + len(window) < total
        items = tuple(self._to_item(row, keyword) for row in window)
        return SearchPageSnapshot(items=items, total=total, page=page, size=size, has_more=has_more)

    def get_hit(self, *, chunk_id: str, tenant_id: int) -> FulltextRow | None:
        """按 chunk_id 回查命中的片段正文（摘录端点）；未装配索引或跨租户返回 None。"""
        if self._search_index is None:
            return None
        return self._search_index.get_chunk(chunk_id, tenant_id=tenant_id)

    # ------------------------------------------------------------------
    # 内部工具
    # ------------------------------------------------------------------

    @classmethod
    def _to_item(cls, row: FulltextRow, keyword: str) -> dict[str, object]:
        """把索引行映射为 API 命中 item（snippet 纯文本 + 0-20 展示分）。"""
        return {
            "document_id": row.document_id,
            "file_name": row.file_name,
            "kb_id": row.kb_id,
            "page_no": row.page_no,
            # section_path（如 [「权限」,「模型」]）拼接为面包屑标题，与问答 sources 一致。
            "section_title": " > ".join(row.section_path) or None,
            "file_ext": row.file_ext,
            "snippet": cls._build_snippet(row.text, keyword),
            # 展示分量纲 0-20（前端按 score/20*100 渲染相关度百分比）。
            "score": round(row.score * _SCORE_SCALE, 2),
            "chunk_id": row.chunk_id,
            "updated_at": row.updated_at,
        }

    @staticmethod
    def _build_snippet(text: str, keyword: str) -> str:
        """生成纯文本摘要片段：命中→以命中位置为中心的窗口；未命中→开头截断。

        前端在客户端对 snippet 做 <mark> 高亮（search/page.tsx），因此这里
        返回原样文本、不做任何标记包裹；前后截断以省略号示意。
        """
        if not text:
            return ""
        # 大小写不敏感定位关键词首次命中。
        index = text.casefold().find(keyword.casefold())
        if index < 0:
            # 未命中（纯语义召回）：返回开头截断文本。
            snippet = text[:_SNIPPET_WINDOW]
            return snippet + "…" if len(text) > _SNIPPET_WINDOW else snippet
        # 命中：窗口左边界前置 1/4（标题/主语常在关键词之前），右侧取满窗口。
        start = max(0, index - _SNIPPET_WINDOW // 4)
        end = min(len(text), start + _SNIPPET_WINDOW)
        prefix = "…" if start > 0 else ""
        suffix = "…" if end < len(text) else ""
        return prefix + text[start:end] + suffix
