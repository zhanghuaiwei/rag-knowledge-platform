"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { api } from "@/api-client";
import type { SearchItem } from "@/api-client";
import { Icon } from "@/components/icons";
import { Empty, ErrorState, Pagination, SkeletonRows, Tag } from "@/components/ui";
import { formatDate } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

function highlight(text: string, keyword: string) {
  if (!keyword) return text;
  const parts = text.split(new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`, "gi"));
  return parts.map((part, i) => (part.toLowerCase() === keyword.toLowerCase() ? <mark key={i}>{part}</mark> : part));
}

function SearchPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get("keyword") ?? "");
  const [submitted, setSubmitted] = useState(searchParams.get("keyword") ?? "");
  const [kbFilter, setKbFilter] = useState("");
  const [page, setPage] = useState(1);

  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));
  const [result, setResult] = useState<{ items: SearchItem[]; total: number } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryTick, setRetryTick] = useState(0);

  // 顶栏全局搜索在本页内再次提交时，同步 URL keyword（组件不重挂载，需监听 searchParams）
  useEffect(() => {
    const kw = searchParams.get("keyword")?.trim() ?? "";
    setSubmitted((prev) => {
      if (!kw || kw === prev) return prev;
      setKeyword(kw);
      setPage(1);
      return kw;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    if (!submitted.trim()) {
      setResult(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .search({
        keyword: submitted,
        kbIds: kbFilter ? [Number(kbFilter)] : undefined,
        page,
        size: 8,
      })
      .then((r) => {
        if (!cancelled) setResult({ items: r.items, total: r.total });
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : "搜索失败");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [submitted, kbFilter, page, retryTick]);

  const doSearch = () => {
    const kw = keyword.trim();
    setPage(1);
    setSubmitted(kw);
    router.replace(`/search?keyword=${encodeURIComponent(kw)}`);
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">全文搜索</h1>
          <p className="page-desc">搜索结果仅包含你有权查看摘要的内容</p>
        </div>
      </div>

      <div className="card">
        <div className="toolbar">
          <div className="topbar-search" style={{ flex: 1, maxWidth: "none", display: "flex" }}>
            <Icon name="search" size={15} />
            <input
              placeholder="输入关键词，支持文件名与正文…"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && doSearch()}
              aria-label="搜索关键词"
            />
          </div>
          <select className="select" value={kbFilter} onChange={(e) => { setKbFilter(e.target.value); setPage(1); }} aria-label="知识库筛选">
            <option value="">全部知识库</option>
            {kbs.data?.items.map((kb) => (
              <option key={kb.id} value={kb.id}>{kb.name}</option>
            ))}
          </select>
          <button className="btn btn-primary" onClick={doSearch} disabled={!keyword.trim()}>
            搜索
          </button>
        </div>
      </div>

      {!submitted ? (
        <div className="card">
          <Empty icon="🔍" title="输入关键词开始搜索" desc="支持按知识库过滤；摘要即 view_excerpt 权限面，打开原文需 view_content" />
        </div>
      ) : loading ? (
        <div className="card"><SkeletonRows rows={5} height={72} /></div>
      ) : error ? (
        <div className="card"><ErrorState message={error} onRetry={() => setRetryTick((t) => t + 1)} /></div>
      ) : result && result.items.length === 0 ? (
        <div className="card">
          <Empty icon="🧐" title={`没有找到与「${submitted}」相关的内容`} desc="可能无匹配，或相关内容不在你的权限范围内" />
        </div>
      ) : (
        <>
          <p style={{ color: "var(--text-2)" }}>共找到 {result?.total ?? 0} 条结果</p>
          {result?.items.map((item) => (
            <div key={`${item.documentId}-${item.pageNo}`} className="card card-hover">
              <div style={{ display: "flex", gap: 12, alignItems: "flex-start" }}>
                <span className="file-icon">{item.fileExt}</span>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Link href={`/documents/${item.documentId}`} style={{ fontWeight: 600, color: "var(--primary)" }}>
                    {highlight(item.fileName, submitted)}
                  </Link>
                  <div style={{ fontSize: 12, color: "var(--text-3)", margin: "2px 0 8px" }}>
                    第 {item.pageNo} 页 · {item.sectionTitle} · 更新于 {formatDate(item.updatedAt)}
                  </div>
                  <p style={{ color: "var(--text-2)" }}>{highlight(item.snippet, submitted)}</p>
                </div>
                <Tag color="primary">{(item.score / 20 * 100).toFixed(0)}%</Tag>
              </div>
            </div>
          ))}
          {result ? <Pagination page={page} size={8} total={result.total} onChange={setPage} /> : null}
        </>
      )}
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={<div className="card"><SkeletonRows rows={4} /></div>}>
      <SearchPageInner />
    </Suspense>
  );
}
