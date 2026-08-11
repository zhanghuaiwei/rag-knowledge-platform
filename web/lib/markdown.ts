/**
 * 轻量 Markdown → HTML 渲染（无第三方依赖，演示级）。
 *
 * 安全说明：先对源文本做 HTML 转义，再应用受控标记替换，渲染结果仅包含
 * 本函数产出的标签，不直接插入未经净化的 HTML（GKB-02 / 设计红线）。
 */

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** 将 Markdown 文本渲染为 HTML 字符串（在受控容器内展示）。 */
export function renderMarkdown(src: string): string {
  let html = escapeHtml(src);
  html = html.replace(/```([\s\S]*?)```/g, (_m, code: string) => `<pre><code>${code.trim()}</code></pre>`);
  html = html.replace(/`([^`\n]+)`/g, "<code>$1</code>");
  html = html.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  html = html.replace(/^### (.*)$/gm, "<h3>$1</h3>");
  html = html.replace(/^## (.*)$/gm, "<h2>$1</h2>");
  html = html.replace(/^# (.*)$/gm, "<h1>$1</h1>");
  html = html.replace(/^> (.*)$/gm, "<blockquote>$1</blockquote>");
  html = html.replace(/^[-*] (.*)$/gm, "<li>$1</li>");
  html = html.replace(/(<li>[\s\S]*?<\/li>\n?)+/g, "<ul>$&</ul>");
  html = html.split(/\n{2,}/).map((block) => block.trim()).filter(Boolean).map((block) => {
    if (/^<(h\d|ul|ol|pre|blockquote)/.test(block)) return block;
    return `<p>${block}</p>`;
  }).join("\n");
  return html;
}
