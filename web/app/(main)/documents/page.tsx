"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { api } from "@/api-client";
import type { IngestStatus, ReviewStatus, Sensitivity } from "@/api-client";
import { Icon } from "@/components/icons";
import { Empty, ErrorState, Pagination, SkeletonRows, Tag } from "@/components/ui";
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

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">文档库</h1>
          <p className="page-desc">跨知识库文档视图，仅显示你有权访问的内容</p>
        </div>
        <div className="page-actions">
          <button className="btn btn-primary" onClick={() => setUploadOpen(true)}>
            <Icon name="upload" size={15} /> 上传文档
          </button>
        </div>
      </div>

      <div className="card">
        <div className="toolbar">
          <input
            className="input"
            placeholder="搜索标题或文件名…"
            value={keywordInput}
            onChange={(e) => setKeywordInput(e.target.value)}
            aria-label="文档关键词"
          />
          <select className="select" value={kbId} onChange={(e) => { setKbId(e.target.value); resetPage(); }} aria-label="知识库">
            <option value="">全部知识库</option>
            {kbs.data?.items.map((kb) => <option key={kb.id} value={kb.id}>{kb.name}</option>)}
          </select>
          <select className="select" value={ingest} onChange={(e) => { setIngest(e.target.value); resetPage(); }} aria-label="摄取状态">
            <option value="">摄取状态</option>
            {INGEST_OPTIONS.map((s) => <option key={s} value={s}>{statusText("ingest", s)[0]}</option>)}
          </select>
          <select className="select" value={review} onChange={(e) => { setReview(e.target.value); resetPage(); }} aria-label="审核状态">
            <option value="">审核状态</option>
            {REVIEW_OPTIONS.map((s) => <option key={s} value={s}>{statusText("review", s)[0]}</option>)}
          </select>
          <select className="select" value={sensitivity} onChange={(e) => { setSensitivity(e.target.value); resetPage(); }} aria-label="敏感级">
            <option value="">敏感级</option>
            {SENS_OPTIONS.map((s) => <option key={s} value={s}>{statusText("sensitivity", s)[0]}</option>)}
          </select>
        </div>
      </div>

      {docs.loading ? (
        <div className="card"><SkeletonRows rows={6} height={56} /></div>
      ) : docs.error ? (
        <div className="card"><ErrorState message={docs.error} onRetry={docs.reload} /></div>
      ) : (docs.data?.items.length ?? 0) === 0 ? (
        <div className="card"><Empty icon="📄" title="没有匹配的文档" desc="调整筛选条件，或文档不在你的权限范围内" /></div>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>文档</th><th>知识库</th><th>大小</th><th>摄取状态</th><th>审核</th><th>敏感级</th><th>所有者</th><th>更新</th></tr>
            </thead>
            <tbody>
              {docs.data?.items.map((doc) => (
                <tr key={doc.id} className="clickable" onClick={() => router.push(`/documents/${doc.id}`)}>
                  <td>
                    <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                      <span className="file-icon">{doc.fileExt}</span>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontWeight: 500 }}>{doc.title}</div>
                        <div style={{ fontSize: 12, color: "var(--text-3)" }}>v{doc.versionNo} · {doc.chunkCount} 分块</div>
                      </div>
                    </div>
                  </td>
                  <td>{doc.kbName}</td>
                  <td>{formatFileSize(doc.fileSize)}</td>
                  <td><Tag color={statusText("ingest", doc.ingestStatus)[1]}>{statusText("ingest", doc.ingestStatus)[0]}</Tag></td>
                  <td><Tag color={statusText("review", doc.reviewStatus)[1]}>{statusText("review", doc.reviewStatus)[0]}</Tag></td>
                  <td><Tag color={statusText("sensitivity", doc.sensitivity)[1]}>{statusText("sensitivity", doc.sensitivity)[0]}</Tag></td>
                  <td>{doc.ownerName}</td>
                  <td>{formatRelative(doc.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {docs.data ? <Pagination page={page} size={10} total={docs.data.total} onChange={setPage} /> : null}

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
    <Suspense fallback={<div className="card"><SkeletonRows rows={5} /></div>}>
      <DocumentsPageInner />
    </Suspense>
  );
}
