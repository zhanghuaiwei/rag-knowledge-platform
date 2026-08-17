"use client";

import { useEffect, useState } from "react";
import { Modal, Spin } from "antd";

import { api } from "@/api-client";
import { ApiError } from "@/api-client/http/errors";
import type { DocumentDetail } from "@/api-client";
import { renderMarkdown } from "@/lib/markdown";

const TEXT_EXTS = ["md", "markdown", "txt", "html"];

/** 由文档详情推导受控预览正文（mock；真实实现为服务端净化的受控预览流）。 */
function mockPreviewBody(doc: DocumentDetail): string {
  const isText = TEXT_EXTS.includes(doc.fileExt.toLowerCase());
  const meta = [
    `**类型**：${doc.mimeType}`,
    `**知识库**：${doc.kbName}`,
    `**版本**：v${doc.versionNo} · ${doc.chunkCount} 分块`,
    `**敏感级**：${doc.sensitivity}`,
  ].join("  \n");
  if (isText) {
    return `# ${doc.title}\n\n${meta}\n\n## 正文预览\n\n（mock 受控预览内容，来自《${doc.fileName}》）\n\n- 该内容经服务端净化后返回，未净化的模型/文档 HTML 不会直接渲染\n- 无 view_content 权限时不渲染原文\n- 历史版本与已撤回内容重新授权，失败给出明确原因`;
  }
  return `# ${doc.title}\n\n${meta}\n\n> 《${doc.fileName}》为二进制格式，预览需服务端转码（PDF.js / mammoth / SheetJS）。当前为受控占位。`;
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
            .catch(() => { /* 读取失败则回退到 mock 正文 */ });
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
        <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", margin: 0, color: "var(--text-2)" }}>{mockPreviewBody(doc)}</pre>
      ) : (
        <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", margin: 0, color: "var(--text-2)" }}>{mockPreviewBody(doc)}</pre>
      )}
    </Modal>
  );
}
