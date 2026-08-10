"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Button, Card, Input, Pagination, Select, Space, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { UploadOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { DocumentSummary, IngestStatus, ReviewStatus, Sensitivity } from "@/api-client";
import { Empty, ErrorState } from "@/components/async-state";
import { UploadDocumentModal } from "@/components/upload-document-modal";
import { formatFileSize, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const INGEST_OPTIONS: IngestStatus[] = ["READY", "PARSING", "INDEXING", "FAILED", "BLOCKED", "QUARANTINED"];
const REVIEW_OPTIONS: ReviewStatus[] = ["DRAFT", "PENDING_REVIEW", "PUBLISHED", "REJECTED", "WITHDRAWN"];
const SENS_OPTIONS: Sensitivity[] = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];

function DocumentsPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [page, setPage] = useState(1);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [kbId, setKbId] = useState(searchParams.get("kbId") ?? "");
  const [ingest, setIngest] = useState("");
  const [review, setReview] = useState("");
  const [sensitivity, setSensitivity] = useState("");
  const [uploadOpen, setUploadOpen] = useState(searchParams.get("upload") === "1");

  // 关键词防抖：停止输入 300ms 后才发起请求
  useEffect(() => {
    const timer = setTimeout(() => {
      setKeyword(keywordInput.trim());
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [keywordInput]);

  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));
  const docs = useAsync(
    () =>
      api.listDocuments({
        page,
        size: 10,
        keyword: keyword || undefined,
        kbId: kbId ? Number(kbId) : undefined,
        ingestStatus: (ingest || undefined) as IngestStatus | undefined,
        reviewStatus: (review || undefined) as ReviewStatus | undefined,
        sensitivity: (sensitivity || undefined) as Sensitivity | undefined,
      }),
    [page, keyword, kbId, ingest, review, sensitivity],
  );

  const resetPage = () => setPage(1);

  const closeUpload = () => {
    setUploadOpen(false);
    // 清理 URL 中的 upload 标记，避免刷新后重复弹出
    if (searchParams.get("upload")) {
      const next = new URLSearchParams(searchParams.toString());
      next.delete("upload");
      router.replace(next.size ? `/documents?${next.toString()}` : "/documents");
    }
  };

  const columns: TableColumnsType<DocumentSummary> = [
    {
      title: "文档",
      key: "title",
      render: (_, doc) => (
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <span className="file-icon">{doc.fileExt}</span>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontWeight: 500 }}>{doc.title}</div>
            <div style={{ fontSize: 12, color: "var(--text-3)" }}>v{doc.versionNo} · {doc.chunkCount} 分块</div>
          </div>
        </div>
      ),
    },
    { title: "知识库", dataIndex: "kbName" },
    {
      title: "大小",
      dataIndex: "fileSize",
      width: 90,
      render: (v: number) => formatFileSize(v),
    },
    {
      title: "摄取状态",
      dataIndex: "ingestStatus",
      width: 110,
      render: (v: IngestStatus) => {
        const [label, color] = statusText("ingest", v);
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: "审核",
      dataIndex: "reviewStatus",
      width: 100,
      render: (v: ReviewStatus) => {
        const [label, color] = statusText("review", v);
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: "敏感级",
      dataIndex: "sensitivity",
      width: 90,
      render: (v: Sensitivity) => {
        const [label, color] = statusText("sensitivity", v);
        return <Tag color={color}>{label}</Tag>;
      },
    },
    { title: "所有者", dataIndex: "ownerName", width: 100 },
    {
      title: "更新",
      dataIndex: "updatedAt",
      width: 110,
      render: (v: string) => formatRelative(v),
    },
  ];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">文档库</h1>
          <p className="page-desc">跨知识库文档视图，仅显示你有权访问的内容</p>
        </div>
        <div className="page-actions">
          <Button type="primary" icon={<UploadOutlined />} onClick={() => setUploadOpen(true)}>
            上传文档
          </Button>
        </div>
      </div>

      <Card>
        <Space wrap>
          <Input
            placeholder="搜索标题或文件名…"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            allowClear
            style={{ width: 220 }}
            aria-label="文档关键词"
          />
          <Select
            placeholder="全部知识库"
            allowClear
            value={kbId || undefined}
            onChange={(v) => { setKbId(v ?? ""); resetPage(); }}
            options={(kbs.data?.items ?? []).map((kb) => ({ value: String(kb.id), label: kb.name }))}
            style={{ width: 160 }}
          />
          <Select
            placeholder="摄取状态"
            allowClear
            value={ingest || undefined}
            onChange={(v) => { setIngest(v ?? ""); resetPage(); }}
            options={INGEST_OPTIONS.map((s) => ({ value: s, label: statusText("ingest", s)[0] }))}
            style={{ width: 130 }}
          />
          <Select
            placeholder="审核状态"
            allowClear
            value={review || undefined}
            onChange={(v) => { setReview(v ?? ""); resetPage(); }}
            options={REVIEW_OPTIONS.map((s) => ({ value: s, label: statusText("review", s)[0] }))}
            style={{ width: 130 }}
          />
          <Select
            placeholder="敏感级"
            allowClear
            value={sensitivity || undefined}
            onChange={(v) => { setSensitivity(v ?? ""); resetPage(); }}
            options={SENS_OPTIONS.map((s) => ({ value: s, label: statusText("sensitivity", s)[0] }))}
            style={{ width: 110 }}
          />
        </Space>
      </Card>

      {docs.error ? (
        <Card>
          <ErrorState message={docs.error} onRetry={docs.reload} />
        </Card>
      ) : (
        <Card>
          <Table<DocumentSummary>
            rowKey="id"
            columns={columns}
            dataSource={docs.data?.items ?? []}
            loading={docs.loading}
            scroll={{ x: 880 }}
            pagination={false}
            onRow={(doc) => ({
              onClick: () => router.push(`/documents/${doc.id}`),
            })}
            locale={{
              emptyText: <Empty icon="📄" title="没有匹配的文档" desc="调整筛选条件，或文档不在你的权限范围内" />,
            }}
          />
          {docs.data ? (
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
              <Pagination current={page} pageSize={10} total={docs.data.total} onChange={setPage} showTotal={(t) => `共 ${t} 条`} />
            </div>
          ) : null}
        </Card>
      )}

      <UploadDocumentModal
        open={uploadOpen}
        defaultKbId={kbId ? Number(kbId) : undefined}
        onClose={closeUpload}
        onUploaded={() => docs.reload()}
      />
    </div>
  );
}

export default function DocumentsPage() {
  return (
    <Suspense fallback={<Card loading />}>
      <DocumentsPageInner />
    </Suspense>
  );
}
