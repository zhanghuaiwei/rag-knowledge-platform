"use client";

import Link from "next/link";
import { useState } from "react";

import { api } from "@/api-client";
import type { ReviewItem } from "@/api-client";
import { Icon } from "@/components/icons";
import { Empty, ErrorState, Modal, Pagination, SkeletonRows, Tag, useToast } from "@/components/ui";
import { formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

export default function ReviewPage() {
  const toast = useToast();
  const [page, setPage] = useState(1);
  const reviews = useAsync(() => api.listReviews({ page, size: 10 }), [page]);

  const [handled, setHandled] = useState<Record<number, "APPROVED" | "REJECTED">>({});
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [action, setAction] = useState<{ kind: "APPROVED" | "REJECTED"; ids: number[] } | null>(null);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const items = (reviews.data?.items ?? []).filter((item) => !handled[item.documentId]);
  const allChecked = items.length > 0 && items.every((i) => selected.has(i.documentId));

  const toggleAll = () => {
    setSelected(allChecked ? new Set() : new Set(items.map((i) => i.documentId)));
  };

  const toggle = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const submitAction = () => {
    if (!action) return;
    if (action.kind === "REJECTED" && !comment.trim()) {
      toast("error", "驳回必须填写审核意见");
      return;
    }
    setSubmitting(true);
    setTimeout(() => {
      setHandled((prev) => {
        const next = { ...prev };
        action.ids.forEach((id) => {
          next[id] = action.kind;
        });
        return next;
      });
      setSelected(new Set());
      setSubmitting(false);
      setComment("");
      toast("success", `已${action.kind === "APPROVED" ? "通过" : "驳回"} ${action.ids.length} 篇文档（mock）`);
      setAction(null);
    }, 600);
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">治理中心 · 审核队列</h1>
          <p className="page-desc">解析就绪 ≠ 可检索；审核通过后内容才进入在线索引</p>
        </div>
        <div className="page-actions">
          <button className="btn" disabled={selected.size === 0} onClick={() => setAction({ kind: "APPROVED", ids: [...selected] })}>
            <Icon name="check" size={15} /> 批量通过{selected.size ? ` (${selected.size})` : ""}
          </button>
          <button className="btn btn-danger" disabled={selected.size === 0} onClick={() => setAction({ kind: "REJECTED", ids: [...selected] })}>
            批量驳回{selected.size ? ` (${selected.size})` : ""}
          </button>
        </div>
      </div>

      {reviews.loading ? (
        <div className="card"><SkeletonRows rows={5} height={60} /></div>
      ) : reviews.error ? (
        <div className="card"><ErrorState message={reviews.error} onRetry={reviews.reload} /></div>
      ) : items.length === 0 ? (
        <div className="card"><Empty icon="✅" title="审核队列已清空" desc="新提交的内容将出现在这里" /></div>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th style={{ width: 36 }}>
                  <input type="checkbox" checked={allChecked} onChange={toggleAll} aria-label="全选" />
                </th>
                <th>文档</th><th>知识库</th><th>敏感级</th><th>提交人</th><th>提交时间</th><th>意见</th><th>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item: ReviewItem) => (
                <tr key={item.documentId}>
                  <td>
                    <input type="checkbox" checked={selected.has(item.documentId)} onChange={() => toggle(item.documentId)} aria-label={`选择 ${item.title}`} />
                  </td>
                  <td>
                    <Link href={`/documents/${item.documentId}`} style={{ color: "var(--primary)", fontWeight: 500 }}>
                      {item.title}
                    </Link>
                  </td>
                  <td>{item.kbName}</td>
                  <td><Tag color={statusText("sensitivity", item.sensitivity)[1]}>{statusText("sensitivity", item.sensitivity)[0]}</Tag></td>
                  <td>{item.submitter}</td>
                  <td>{formatRelative(item.submittedAt)}</td>
                  <td>{item.commentCount} 条</td>
                  <td>
                    <div style={{ display: "flex", gap: 6 }}>
                      <button className="btn btn-sm" onClick={() => setAction({ kind: "APPROVED", ids: [item.documentId] })}>通过</button>
                      <button className="btn btn-sm btn-ghost" style={{ color: "var(--danger)" }} onClick={() => setAction({ kind: "REJECTED", ids: [item.documentId] })}>驳回</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {reviews.data ? <Pagination page={page} size={10} total={reviews.data.total - Object.keys(handled).length} onChange={setPage} /> : null}

      <Modal
        title={action?.kind === "APPROVED" ? "通过审核" : "驳回审核"}
        open={action !== null}
        onClose={() => setAction(null)}
        footer={
          <>
            <button className="btn" onClick={() => setAction(null)} disabled={submitting}>取消</button>
            <button className={`btn ${action?.kind === "REJECTED" ? "btn-danger" : "btn-primary"}`} onClick={submitAction} disabled={submitting}>
              {submitting ? "提交中…" : "确认提交"}
            </button>
          </>
        }
      >
        <p style={{ marginBottom: 14, color: "var(--text-2)" }}>
          将对 {action?.ids.length ?? 0} 篇文档执行「{action?.kind === "APPROVED" ? "通过并发布" : "驳回"}」。
          {action?.kind === "APPROVED" ? "发布后进入在线索引，传播完成前按数据库策略二次过滤。" : "驳回后作者可修改重新提交。"}
        </p>
        <div className="field" style={{ marginBottom: 0 }}>
          <label className="field-label">
            审核意见{action?.kind === "REJECTED" ? <span className="req">*</span> : "（可选）"}
          </label>
          <textarea className="textarea" value={comment} onChange={(e) => setComment(e.target.value)} placeholder="填写审核意见…" />
        </div>
      </Modal>
    </div>
  );
}
