"use client";

import { useEffect, useRef, useState } from "react";

import { api } from "@/api-client";
import type { DocumentSummary, Sensitivity } from "@/api-client";
import { Icon } from "@/components/icons";
import { Modal, useToast } from "@/components/ui";
import { formatFileSize } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const ACCEPT = ".pdf,.doc,.docx,.md,.txt,.pptx,.xlsx,.csv,.html";
const MAX_SIZE = 50 * 1024 * 1024;

/**
 * 文档上传弹窗（mock 演示）：真实实现为分片上传 + 安全扫描队列（GKB-03），
 * 当前经 api.uploadDocument 进入 mock 列表，摄取状态从 PARSING 开始。
 */
export function UploadDocumentModal({
  open,
  defaultKbId,
  onClose,
  onUploaded,
}: {
  open: boolean;
  defaultKbId?: number;
  onClose: () => void;
  onUploaded: (doc: DocumentSummary) => void;
}) {
  const toast = useToast();
  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));
  const fileRef = useRef<HTMLInputElement>(null);

  const [kbId, setKbId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [sensitivity, setSensitivity] = useState<Sensitivity>("INTERNAL");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  // 打开时按上下文重置表单
  useEffect(() => {
    if (!open) return;
    setKbId(defaultKbId ? String(defaultKbId) : "");
    setFile(null);
    setTitle("");
    setSensitivity("INTERNAL");
    setError("");
    setSubmitting(false);
  }, [open, defaultKbId]);

  const pickFile = (selected: File | null) => {
    setFile(selected);
    setError("");
    if (selected && !title) setTitle(selected.name.replace(/\.[^.]+$/, ""));
  };

  const submit = async () => {
    if (!kbId) {
      setError("请选择目标知识库");
      return;
    }
    if (!file) {
      setError("请选择要上传的文件");
      return;
    }
    if (file.size > MAX_SIZE) {
      setError(`文件超过 ${formatFileSize(MAX_SIZE)} 上限`);
      return;
    }
    setSubmitting(true);
    try {
      const doc = await api.uploadDocument({
        kbId: Number(kbId),
        title: title.trim() || file.name,
        fileName: file.name,
        fileSize: file.size,
        sensitivity,
      });
      toast("success", `「${doc.title}」已上传，进入解析队列`);
      onUploaded(doc);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "上传失败，请重试");
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="上传文档"
      open={open}
      onClose={submitting ? () => undefined : onClose}
      footer={
        <>
          <button className="btn" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button className="btn btn-primary" onClick={() => void submit()} disabled={submitting}>
            {submitting ? "上传中…" : "开始上传"}
          </button>
        </>
      }
    >
      <div className="field">
        <label className="field-label">
          目标知识库<span className="req">*</span>
        </label>
        <select className="select" value={kbId} onChange={(e) => setKbId(e.target.value)} aria-label="目标知识库">
          <option value="">请选择</option>
          {kbs.data?.items
            .filter((kb) => kb.role !== "VIEWER" && kb.status === "ACTIVE")
            .map((kb) => (
              <option key={kb.id} value={kb.id}>
                {kb.name}
              </option>
            ))}
        </select>
      </div>

      <div className="field">
        <label className="field-label">
          文件<span className="req">*</span>
        </label>
        <input
          ref={fileRef}
          type="file"
          accept={ACCEPT}
          style={{ display: "none" }}
          onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
        />
        <button type="button" className="btn btn-block" onClick={() => fileRef.current?.click()}>
          <Icon name="upload" size={15} />
          {file ? `${file.name}（${formatFileSize(file.size)}）` : "选择文件（PDF / Word / Markdown 等）"}
        </button>
        <p className="field-hint">单文件不超过 {formatFileSize(MAX_SIZE)}；上传后先经安全扫描再进入解析队列</p>
      </div>

      <div className="field">
        <label className="field-label">标题</label>
        <input
          className="input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="默认取文件名"
          maxLength={80}
        />
      </div>

      <div className="field" style={{ marginBottom: 0 }}>
        <label className="field-label">敏感级</label>
        <select className="select" value={sensitivity} onChange={(e) => setSensitivity(e.target.value as Sensitivity)} aria-label="敏感级">
          <option value="PUBLIC">公开</option>
          <option value="INTERNAL">内部</option>
          <option value="CONFIDENTIAL">机密</option>
          <option value="RESTRICTED">受限</option>
        </select>
      </div>

      {error ? <p className="field-error" style={{ marginTop: 12 }}>{error}</p> : null}
    </Modal>
  );
}
