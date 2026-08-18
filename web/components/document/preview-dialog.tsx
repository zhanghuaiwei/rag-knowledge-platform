"use client";

import { useEffect, useState } from "react";
import { Modal, Spin } from "antd";

import { api } from "@/api-client";
import { ApiError } from "@/api-client/http/errors";
import type { DocumentDetail } from "@/api-client";
import { renderMarkdown } from "@/lib/markdown";

const TEXT_EXTS = ["md", "markdown", "txt", "html"];

/**
 * 受控占位正文：仅在两类场景渲染——
 * ① 文本类文件的真实预览流读取失败（回退，不渲染不确定内容）；
 * ② 二进制办公格式（docx/pptx/xlsx 等）：需服务端转码后才能安全渲染，
 *    当前仅 PDF/图片/文本走真实文件流，此类格式保留占位提示。
 */
function placeholderBody(doc: DocumentDetail): string {
  const isText = TEXT_EXTS.includes(doc.fileExt.toLowerCase());
  const meta = [
    `**类型**：${doc.mimeType}`,
    `**知识库**：${doc.kbName}`,
    `**版本**：v${doc.versionNo} · ${doc.chunkCount} 分块`,
    `**敏感级**：${doc.sensitivity}`,
  ].join("  \n");
  if (isText) {
    return `# ${doc.title}\n\n${meta}\n\n## 正文预览\n\n（预览内容暂时不可用，文件：《${doc.fileName}》）\n\n- 正文需从服务端预览流读取，读取失败时不渲染不确定内容\n- 无 VIEW_CONTENT 权限时不渲染原文\n- 历史版本与已撤回内容重新授权，失败给出明确原因`;
  }
  return `# ${doc.title}\n\n${meta}\n\n> 《${doc.fileName}》为二进制办公格式，需服务端转码后渲染（转码能力待接入）。当前展示占位提示。`;
}

/**
 * 文档预览弹窗：
 * - 打开时从后端 GET /documents/{id}/preview 获取文件字节流 → Blob URL
 * - PDF：iframe 直接渲染（浏览器内置 PDF Viewer）
 * - 图片：img 直接渲染
 * - 文本/MD：读取文本内容，MD 走 Markdown 渲染，其余纯文本展示
 * - 其他格式：回退到受控占位提示
 * - 权限不足（403）时展示提示，不弹错误
 */
export function PreviewDialog({
  open,
  onClose,
  doc,
  targetPage,
}: {
  open: boolean;
  onClose: () => void;
  doc: DocumentDetail;
  targetPage?: number | null;
}) {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [textContent, setTextContent] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string>("");

  const isText = TEXT_EXTS.includes(doc.fileExt.toLowerCase());
  const isPdf = doc.fileExt.toLowerCase() === "pdf";
  const isImage = ["png", "jpg", "jpeg", "gif", "svg", "webp"].includes(doc.fileExt.toLowerCase());

  // 打开时拉取文件流；关闭时释放 Blob URL
  useEffect(() => {
    if (!open) return;
    let url: string | null = null;
    let revoked = false;
    setLoading(true);
    setError("");
    setBlobUrl(null);
    setTextContent(null);

    api.previewDocument(doc.id)
      .then((generatedUrl) => {
        if (revoked) {
          URL.revokeObjectURL(generatedUrl);
          return;
        }
        url = generatedUrl;
        setBlobUrl(url);
        // 文本类文件：读取内容用于渲染（MD→Markdown，其余→纯文本）
        if (isText) {
          fetch(url)
            .then((resp) => resp.text())
            .then((text) => !revoked && setTextContent(text))
            .catch(() => { /* 文本流读取失败则回退到受控占位正文 */ });
        }
      })
      .catch((err: unknown) => {
        if (revoked) return;
        if (err instanceof ApiError && err.isForbidden) {
          setError("无预览权限（需要 VIEW_CONTENT），请联系管理员授权");
        } else {
          setError(err instanceof Error ? err.message : "预览加载失败");
        }
      })
      .finally(() => !revoked && setLoading(false));

    return () => {
      revoked = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [open, doc.id, isText]);

  return (
    <Modal title={`预览 · ${doc.fileName}`} open={open} onCancel={onClose} footer={null} width={860} destroyOnClose>
      {targetPage ? (
        <div style={{ marginBottom: 12, padding: "8px 12px", background: "var(--surface-2)", borderRadius: 8, fontSize: 13, color: "var(--text-2)" }}>
          🔗 已定位到第 {targetPage} 页 / 命中片段所在章节
        </div>
      ) : null}

      {loading ? (
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: "48px 0", color: "var(--text-3)" }}>
          <Spin />
          <span>正在加载文件流...</span>
        </div>
      ) : error ? (
        <div style={{ padding: "32px 0", textAlign: "center", color: "var(--text-3)" }}>
          <p style={{ marginBottom: 8 }}>{error}</p>
        </div>
      ) : isPdf && blobUrl ? (
        <iframe src={blobUrl} style={{ width: "100%", height: "70vh", border: "none", borderRadius: 8 }} title="PDF 预览" />
      ) : isImage && blobUrl ? (
        <div style={{ textAlign: "center" }}>
          <img src={blobUrl} alt={doc.fileName} style={{ maxWidth: "100%", borderRadius: 8 }} />
        </div>
      ) : isText && textContent != null ? (
        <div className="markdown-body" dangerouslySetInnerHTML={{ __html: renderMarkdown(textContent) }} />
      ) : isText ? (
        // 文本流读取失败：回退到受控占位正文，不渲染不确定内容
        <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", margin: 0, color: "var(--text-2)" }}>{placeholderBody(doc)}</pre>
      ) : (
        // 二进制办公格式（docx 等）：等待服务端转码能力，展示占位提示
        <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", margin: 0, color: "var(--text-2)" }}>{placeholderBody(doc)}</pre>
      )}
    </Modal>
  );
}
