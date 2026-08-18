"""全文搜索用例单元测试（fake 索引，不依赖 pgvector/Embedding）。"""

from __future__ import annotations

from rag_engine.rerank.service import RerankService
from rag_engine.retrieval.models import FulltextQuery, FulltextRow
from rag_engine.retrieval.service import RetrievalService


class FakeSearchIndex:
    """内存假索引：按构造时给定的行与总数返回，记录收到的查询供断言。"""

    def __init__(self, rows: list[FulltextRow], total: int | None = None) -> None:
        self._rows = rows
        self._total = total if total is not None else len(rows)
        self.captured_query: FulltextQuery | None = None

    def fulltext_search(self, query: FulltextQuery) -> tuple[list[FulltextRow], int]:
        self.captured_query = query
        # offset/limit 窗口切片（模拟 SQL 分页语义）。
        return self._rows[query.offset : query.offset + query.limit], self._total

    def get_chunk(self, chunk_id: str, *, tenant_id: int) -> FulltextRow | None:
        for row in self._rows:
            if row.chunk_id == chunk_id:
                return row
        return None


def _row(chunk_id: str, text: str, *, score: float, document_id: int = 1,
         section_path: list[str] | None = None, updated_at: str | None = None) -> FulltextRow:
    """构造测试用索引行。"""
    return FulltextRow(
        chunk_id=chunk_id,
        document_id=document_id,
        version_id=document_id * 10,
        kb_id=2,
        text=text,
        file_name="权限设计.pdf",
        file_ext="pdf",
        page_no=3,
        section_path=section_path or [],
        updated_at=updated_at,
        score=score,
    )


def test_search_without_index_returns_stable_empty_page() -> None:
    """fail-closed：未装配索引时返回确定性空分页，不抛异常。"""
    service = RetrievalService()
    result = service.search(
        request_id="req-1", keyword="权限", kb_ids=[], doc_id_whitelist=[],
        file_types=[], date_from=None, date_to=None,
        vector_fusion=True, page=3, size=5,
    )
    assert result.items == ()
    assert result.total == 0
    assert result.has_more is False


def test_search_maps_rows_to_items_with_score_scale_and_section() -> None:
    """命中映射：score 放大到 0-20 量纲、section_path 拼接为标题、chunk_id 透传。"""
    row = _row(
        "a" * 64, "角色权限模型说明", score=0.5,
        section_path=["系统设计", "权限"], updated_at="2026-08-01T00:00:00",
    )
    index = FakeSearchIndex([row])
    service = RetrievalService(search_index=index)
    result = service.search(
        request_id="req", keyword="权限", kb_ids=[2], doc_id_whitelist=[],
        file_types=["pdf"], date_from=None, date_to=None,
        vector_fusion=False, page=1, size=10,
    )
    item = result.items[0]
    assert item["score"] == 10.0  # 0.5 * 20
    assert item["section_title"] == "系统设计 > 权限"
    assert item["chunk_id"] == "a" * 64
    assert item["updated_at"] == "2026-08-01T00:00:00"
    assert item["document_id"] == 1
    # 过滤条件与分页窗口原样传给索引层。
    assert index.captured_query is not None
    assert index.captured_query.tenant_id == 1
    assert index.captured_query.kb_ids == [2]
    assert index.captured_query.file_types == ["pdf"]
    assert index.captured_query.offset == 0
    assert index.captured_query.limit == 10


def test_search_tenant_id_is_passed_through_to_index() -> None:
    """多租户红线：tenant_id 必须贯穿到索引查询。"""
    index = FakeSearchIndex([])
    service = RetrievalService(search_index=index)
    service.search(
        request_id="req", keyword="x", kb_ids=[], doc_id_whitelist=[],
        file_types=[], date_from=None, date_to=None,
        vector_fusion=False, page=1, size=10, tenant_id=7,
    )
    assert index.captured_query is not None
    assert index.captured_query.tenant_id == 7


def test_search_pagination_offset_and_has_more() -> None:
    """offset 分页：page=2 → offset=size；has_more 按 total 判定。"""
    rows = [_row(f"{i:064x}", f"第{i}段权限说明", score=0.4) for i in range(7)]
    index = FakeSearchIndex(rows)
    service = RetrievalService(search_index=index)
    result = service.search(
        request_id="req", keyword="权限", kb_ids=[], doc_id_whitelist=[],
        file_types=[], date_from=None, date_to=None,
        vector_fusion=False, page=2, size=3,
    )
    assert index.captured_query is not None
    assert index.captured_query.offset == 3
    assert index.captured_query.limit == 3
    assert result.total == 7
    assert len(result.items) == 3
    # has_more 判定：offset(3) + 窗口(3) = 6 < total(7) → 还有下一页。
    assert result.has_more is True


def test_search_fusion_promotes_keyword_coverage_over_pure_vector() -> None:
    """融合重排：词项全覆盖候选（向量分低）应排到纯语义候选（向量分高）之前。"""
    # 语义近但词项不覆盖：融合分 0.6*0.9 + 0.4*0 ≈ 0.54。
    semantic = _row("b" * 64, "访问控制模型", score=0.9)
    # 词项全覆盖但向量分低：融合分 0.6*0.5 + 0.4*1.0 = 0.70。
    keyword_hit = _row("c" * 64, "角色权限模型", score=0.5)
    index = FakeSearchIndex([semantic, keyword_hit])
    service = RetrievalService(search_index=index, reranker=RerankService(enabled=True))
    result = service.search(
        request_id="req", keyword="权限", kb_ids=[], doc_id_whitelist=[],
        file_types=[], date_from=None, date_to=None,
        vector_fusion=True, page=1, size=10,
    )
    # 融合模式取放大窗口（size*3），重排后关键词命中排第一。
    assert index.captured_query is not None
    assert index.captured_query.limit == 30
    assert result.items[0]["chunk_id"] == "c" * 64
    assert result.items[1]["chunk_id"] == "b" * 64


def test_search_fusion_degrades_to_vector_order_without_reranker() -> None:
    """精排不可用（disabled）时退化为纯向量序，不报错（fail-closed）。"""
    rows = [_row("d" * 64, "无关文本", score=0.9), _row("e" * 64, "角色权限模型", score=0.5)]
    index = FakeSearchIndex(rows)
    service = RetrievalService(search_index=index, reranker=RerankService(enabled=False))
    result = service.search(
        request_id="req", keyword="权限", kb_ids=[], doc_id_whitelist=[],
        file_types=[], date_from=None, date_to=None,
        vector_fusion=True, page=1, size=10,
    )
    # 纯向量序：高分在前；窗口直接取 size（不放大）。
    assert index.captured_query is not None
    assert index.captured_query.limit == 10
    assert result.items[0]["chunk_id"] == "d" * 64


def test_snippet_keyword_hit_returns_centered_window() -> None:
    """snippet：关键词命中→以命中位置为中心的窗口原样文本。"""
    service = RetrievalService()
    text = "前缀" + "垫" * 300 + "权限模型" + "后" * 300
    snippet = service._build_snippet(text, "权限模型")
    assert "权限模型" in snippet
    assert snippet.startswith("…") and snippet.endswith("…")
    assert len(snippet) <= 202  # 窗口 200 + 两个省略号


def test_snippet_miss_returns_truncated_head() -> None:
    """snippet：未命中→返回开头截断文本。"""
    service = RetrievalService()
    text = "访问控制" * 100
    snippet = service._build_snippet(text, "不存在关键词")
    assert snippet == text[:200] + "…"
    assert service._build_snippet("短文本", "关键词") == "短文本"


def test_get_hit_delegates_to_index_with_tenant() -> None:
    """摘录回查：委托索引层按 chunk_id + tenant_id 查询。"""
    row = _row("f" * 64, "角色权限模型", score=0.1)
    service = RetrievalService(search_index=FakeSearchIndex([row]))
    hit = service.get_hit(chunk_id="f" * 64, tenant_id=3)
    assert hit is not None and hit.text == "角色权限模型"
    # 不存在的命中 / 未装配索引均返回 None（deny-by-default）。
    assert service.get_hit(chunk_id="0" * 64, tenant_id=3) is None
    assert RetrievalService().get_hit(chunk_id="f" * 64, tenant_id=3) is None
