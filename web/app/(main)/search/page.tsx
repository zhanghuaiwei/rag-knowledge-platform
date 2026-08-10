"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Button, Card, Form, Input, Pagination, Select, Tag } from "antd";
import { SearchOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { SearchItem } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { formatDate } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

interface SearchFormValues {
  keyword?: string;
  kbId?: number;
}

function highlight(text: string, keyword: string) {
  if (!keyword) return text;
  const parts = text.split(new RegExp(`(${keyword.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`, "gi"));
  return parts.map((part, i) => (part.toLowerCase() === keyword.toLowerCase() ? <mark key={i}>{part}</mark> : part));
}

function SearchPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [form] = Form.useForm<SearchFormValues>();
  const initialKeyword = searchParams.get("keyword") ?? "";
  const [query, setQuery] = useState<{ keyword: string; kbId?: number }>({ keyword: initialKeyword });
  const [page, setPage] = useState(1);

  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));
  const [result, setResult] = useState<{ items: SearchItem[]; total: number } | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryTick, setRetryTick] = useState(0);

  // 顶栏全局搜索在本页内再次提交时，同步 URL keyword（组件不重挂载，需监听 searchParams）
  useEffect(() => {
    const kw = searchParams.get("keyword")?.trim() ?? "";
    if (kw && kw !== query.keyword) {
      form.setFieldValue("keyword", kw);
      setQuery((prev) => ({ keyword: kw, kbId: prev.kbId }));
      setPage(1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    if (!query.keyword.trim()) {
      setResult(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .search({
        keyword: query.keyword,
        kbIds: query.kbId ? [query.kbId] : undefined,
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
  }, [query, page, retryTick]);

  const onFinish = (values: SearchFormValues) => {
    const kw = values.keyword?.trim() ?? "";
    setPage(1);
    setQuery({ keyword: kw, kbId: values.kbId });
    router.replace(`/search?keyword=${encodeURIComponent(kw)}`);
  };

  const submitted = query.keyword.trim();

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">全文搜索</h1>
          <p className="page-desc">搜索结果仅包含你有权查看摘要的内容</p>
        </div>
      </div>

      <Card>
        <Form<SearchFormValues> form={form} layout="inline" onFinish={onFinish} initialValues={{ keyword: initialKeyword }}>
          <Form.Item name="keyword" style={{ flex: 1 }}>
            <Input
              prefix={<SearchOutlined />}
              placeholder="输入关键词，支持文件名与正文…"
              allowClear
              onPressEnter={() => form.submit()}
            />
          </Form.Item>
          <Form.Item name="kbId">
            <Select
              placeholder="全部知识库"
              allowClear
              style={{ minWidth: 180 }}
              options={(kbs.data?.items ?? []).map((kb) => ({ value: kb.id, label: kb.name }))}
            />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" disabled={!form.getFieldValue("keyword")}>
              搜索
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {!submitted ? (
        <Card>
          <Empty icon="🔍" title="输入关键词开始搜索" desc="支持按知识库过滤；摘要即 view_excerpt 权限面，打开原文需 view_content" />
        </Card>
      ) : loading ? (
        <Card>
          <SkeletonRows rows={5} height={72} />
        </Card>
      ) : error ? (
        <Card>
          <ErrorState message={error} onRetry={() => setRetryTick((t) => t + 1)} />
        </Card>
      ) : result && result.items.length === 0 ? (
        <Card>
          <Empty icon="🧐" title={`没有找到与「${submitted}」相关的内容`} desc="可能无匹配，或相关内容不在你的权限范围内" />
        </Card>
      ) : (
        <>
          <p style={{ color: "var(--text-2)" }}>共找到 {result?.total ?? 0} 条结果</p>
          {(result?.items ?? []).map((item) => (
            <Card key={`${item.documentId}-${item.pageNo}`} hoverable className="card-hover" style={{ marginBottom: 12 }}>
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
                <Tag color="blue">{(item.score / 20 * 100).toFixed(0)}%</Tag>
              </div>
            </Card>
          ))}
          {result ? (
            <div style={{ display: "flex", justifyContent: "flex-end" }}>
              <Pagination current={page} pageSize={8} total={result.total} onChange={setPage} showTotal={(t) => `共 ${t} 条结果`} />
            </div>
          ) : null}
        </>
      )}
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense fallback={<Card><SkeletonRows rows={4} /></Card>}>
      <SearchPageInner />
    </Suspense>
  );
}
