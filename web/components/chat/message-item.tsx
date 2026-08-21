"use client";

import { useState, type ReactElement } from "react";
import { Tag } from "antd";

import type { DisplayMessage } from "@/components/chat/types";
import { Icon } from "@/components/icons";
import { formatPercent, statusText } from "@/lib/format";

/** 简易 Markdown 渲染：支持代码块(```)、行内代码(`)、粗体(**)、斜体(*)、列表、换行。
 *  不引入第三方库，保持轻量；复杂 Markdown 由后续迭代增强。 */
function renderMarkdown(text: string): ReactElement {
  const lines = text.split("\n");
  const elements: ReactElement[] = [];
  let i = 0;
  let key = 0;

  while (i < lines.length) {
    const line = lines[i];

    // 代码块检测
    const codeMatch = line.match(/^```(\w*)\s*$/);
    if (codeMatch) {
      const lang = codeMatch[1] || "text";
      const codeLines: string[] = [];
      i++;
      while (i < lines.length && !lines[i].match(/^```\s*$/)) {
        codeLines.push(lines[i]);
        i++;
      }
      i++; // skip closing ```
      elements.push(
        <pre key={`code-${key++}`} className="chat-code-block">
          <code>
            <span className="chat-code-lang">{lang}</span>
            {codeLines.join("\n")}
          </code>
        </pre>,
      );
      continue;
    }

    // 列表检测
    const listMatch = line.match(/^(\s*)([-*]|\d+\.)\s+(.+)/);
    if (listMatch) {
      const items: string[] = [];
      const indent = listMatch[1].length;
      const marker = listMatch[2];
      while (i < lines.length) {
        const cur = lines[i];
        const m = cur.match(/^(\s*)([-*]|\d+\.)\s+(.+)/);
        if (m && m[1].length === indent) {
          items.push(m[3]);
          i++;
        } else {
          break;
        }
      }
      const ordered = /\d+\./.test(marker);
      const ListTag = ordered ? "ol" : "ul";
      elements.push(
        <ListTag key={`list-${key++}`} className="chat-list" style={{ paddingLeft: 20, margin: "4px 0" }}>
          {items.map((item, idx) => (
            <li key={idx}>{renderInline(item)}</li>
          ))}
        </ListTag>,
      );
      continue;
    }

    // 空行
    if (line.trim() === "") {
      elements.push(<div key={`empty-${key++}`} style={{ height: 6 }} />);
      i++;
      continue;
    }

    // 普通段落
    elements.push(<p key={`p-${key++}`} style={{ margin: "4px 0", lineHeight: 1.7 }}>{renderInline(line)}</p>);
    i++;
  }

  return <div className="chat-markdown">{elements}</div>;
}

/** 行内格式：粗体、斜体、行内代码、链接 */
function renderInline(text: string): ReactElement {
  // 按优先级拆分：先代码块，再粗体，再斜体
  const parts: (string | ReactElement)[] = [];
  let remaining = text;
  let k = 0;

  // 处理行内代码 `...`
  const codeRegex = /`([^`]+)`/g;
  let lastIdx = 0;
  let match: RegExpExecArray | null;
  const segments: (string | ReactElement)[] = [];

  while ((match = codeRegex.exec(text)) !== null) {
    if (match.index > lastIdx) segments.push(text.slice(lastIdx, match.index));
    segments.push(<code key={`c-${k++}`} className="chat-inline-code">{match[1]}</code>);
    lastIdx = match.index + match[0].length;
  }
  if (lastIdx < text.length) segments.push(text.slice(lastIdx));

  // 对每个非代码段处理粗体/斜体
  const finalParts: (string | ReactElement)[] = [];
  segments.forEach((seg, idx) => {
    if (typeof seg !== "string") {
      finalParts.push(seg);
      return;
    }
    // 粗体 **...**
    const boldRegex = /\*\*([^*]+)\*\*/g;
    let last = 0;
    let bm: RegExpExecArray | null;
    const boldParts: (string | ReactElement)[] = [];
    while ((bm = boldRegex.exec(seg)) !== null) {
      if (bm.index > last) boldParts.push(seg.slice(last, bm.index));
      boldParts.push(<strong key={`b-${idx}-${k++}`}>{bm[1]}</strong>);
      last = bm.index + bm[0].length;
    }
    if (last < seg.length) boldParts.push(seg.slice(last));

    // 对粗体处理后的字符串段再处理斜体 *...*
    boldParts.forEach((bp, bidx) => {
      if (typeof bp !== "string") {
        finalParts.push(bp);
        return;
      }
      const italicRegex = /\*([^*]+)\*/g;
      let ilast = 0;
      let im: RegExpExecArray | null;
      while ((im = italicRegex.exec(bp)) !== null) {
        if (im.index > ilast) finalParts.push(bp.slice(ilast, im.index));
        finalParts.push(<em key={`i-${idx}-${bidx}-${k++}`}>{im[1]}</em>);
        ilast = im.index + im[0].length;
      }
      if (ilast < bp.length) finalParts.push(bp.slice(ilast));
    });
  });

  // 清理 unused variable warning
  void remaining;
  void parts;

  return <>{finalParts}</>;
}

/** 单条问答消息：气泡 + 置信度 + 来源引用 + 追问建议 + 反馈 + 复制/重试/重新生成。 */
export function MessageItem({
  msg,
  onGiveFeedback,
  onOpenSource,
  onSendSuggestion,
  onRetry,
}: {
  msg: DisplayMessage;
  onGiveFeedback: (value: -1 | 1) => void;
  onOpenSource: (documentId: number) => void;
  onSendSuggestion: (text: string) => void;
  onRetry?: () => void;
}) {
  const [copied, setCopied] = useState(false);
  const isFailed = msg.answerStatus === "BLOCKED" || (msg.content === "回答生成失败，请重试" && !msg.streaming);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(msg.content);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      // fallback
      const textarea = document.createElement("textarea");
      textarea.value = msg.content;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand("copy");
      document.body.removeChild(textarea);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }
  };

  const showAssistantActions = msg.role === "ASSISTANT" && !msg.streaming;

  return (
    <div className={`chat-msg ${msg.role === "USER" ? "user" : "assistant"}`}>
      <span
        className="avatar"
        style={msg.role === "ASSISTANT" ? { background: "linear-gradient(135deg,var(--violet),var(--primary))" } : undefined}
      >
        {msg.role === "USER" ? "我" : "AI"}
      </span>
      <div style={{ minWidth: 0, flex: 1 }}>
        <div className={`chat-bubble ${isFailed ? "bubble-error" : ""}`}>
          {msg.role === "ASSISTANT" && !msg.streaming ? renderMarkdown(msg.content) : msg.content}
          {msg.streaming ? <span className="stream-cursor" /> : null}
        </div>

        {msg.role === "USER" ? (
          <div className="chat-meta">
            <button
              className="feedback-btn"
              onClick={handleCopy}
              aria-label="复制"
              title={copied ? "已复制" : "复制消息"}
            >
              <Icon name={copied ? "check" : "copy"} size={15} />
            </button>
          </div>
        ) : null}

        {showAssistantActions ? (
          <>
            <div className="chat-meta">
              {msg.answerStatus ? (
                <Tag color={statusText("answer", msg.answerStatus)[1]}>
                  {statusText("answer", msg.answerStatus)[0]}
                </Tag>
              ) : null}
              {typeof msg.confidence === "number" ? (
                <span className="confidence-bar">
                  置信度
                  <span className="confidence-track">
                    <span
                      className="confidence-fill"
                      style={{
                        width: `${msg.confidence * 100}%`,
                        background:
                          msg.confidence >= 0.8
                            ? "var(--success)"
                            : msg.confidence >= 0.6
                              ? "var(--warning)"
                              : "var(--danger)",
                      }}
                    />
                  </span>
                  {formatPercent(msg.confidence)}
                </span>
              ) : null}
              <button
                className={`feedback-btn${msg.feedback === 1 ? " active-good" : ""}`}
                onClick={() => onGiveFeedback(1)}
                aria-label="有用"
                title="有用"
              >
                <Icon name="thumbs-up" size={15} />
              </button>
              <button
                className={`feedback-btn${msg.feedback === -1 ? " active-bad" : ""}`}
                onClick={() => onGiveFeedback(-1)}
                aria-label="无用"
                title="无用"
              >
                <Icon name="thumbs-down" size={15} />
              </button>
              <button
                className="feedback-btn"
                onClick={handleCopy}
                aria-label="复制"
                title={copied ? "已复制" : "复制回答"}
              >
                <Icon name={copied ? "check" : "copy"} size={15} />
              </button>
              {onRetry ? (
                <button
                  className="feedback-btn"
                  onClick={onRetry}
                  aria-label="重新生成"
                  title="重新生成回答"
                >
                  <Icon name="refresh" size={15} />
                </button>
              ) : null}
            </div>

            {isFailed && onRetry ? (
              <div className="chat-retry-bar">
                <span className="chat-retry-hint">回答生成失败</span>
                <button className="chat-retry-btn" onClick={onRetry}>
                  <Icon name="refresh" size={14} />
                  重试
                </button>
              </div>
            ) : null}

            {msg.sources && msg.sources.length > 0 ? (
              <div className="chat-sources">
                {msg.sources.map((s) => (
                  <div key={s.chunkId} className="chat-source" onClick={() => onOpenSource(s.documentId)}>
                    <Icon name="doc" size={14} />
                    <span
                      style={{
                        flex: 1,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {s.fileName} · 第 {s.pageNo} 页 · {s.sectionTitle}
                    </span>
                    <Tag>{formatPercent(s.score)}</Tag>
                  </div>
                ))}
              </div>
            ) : null}

            {msg.suggestions && msg.suggestions.length > 0 ? (
              <div className="chat-suggestions">
                {msg.suggestions.map((sug) => (
                  <button key={sug} className="chat-suggestion" onClick={() => onSendSuggestion(sug)}>
                    {sug}
                  </button>
                ))}
              </div>
            ) : null}
          </>
        ) : null}
      </div>
    </div>
  );
}
