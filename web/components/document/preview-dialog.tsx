"use client";

import { Modal, Spin } from "antd";

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

/** 文档预览弹窗：MD/TXT 用 Markdown 渲染，PDF 用受控占位，其余回退纯文本。 */
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
  const isText = TEXT_EXTS.includes(doc.fileExt.toLowerCase());
  const isPdf = doc.fileExt.toLowerCase() === "pdf";

  return (
    <Modal title={`预览 · ${doc.fileName}`} open={open} onCancel={onClose} footer={null} width={760}>
      {targetPage ? (
        <div style={{ marginBottom: 12, padding: "8px 12px", background: "var(--surface-2)", borderRadius: 8, fontSize: 13, color: "var(--text-2)" }}>
          🔗 已定位到第 {targetPage} 页 / 命中片段所在章节
        </div>
      ) : null}

      {isText ? (
        <div className="markdown-body" dangerouslySetInnerHTML={{ __html: renderMarkdown(mockPreviewBody(doc)) }} />
      ) : isPdf ? (
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: "48px 0", color: "var(--text-3)" }}>
          <Spin />
          <span>PDF 预览：真实环境走服务端代理文件流 + PDF.js 渲染并定位到指定页；此处为受控占位。</span>
        </div>
      ) : (
        <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", margin: 0, color: "var(--text-2)" }}>{mockPreviewBody(doc)}</pre>
      )}
    </Modal>
  );
}
