"use client";

import { Tag } from "antd";

import type { DisplayMessage } from "@/components/chat/types";
import { Icon } from "@/components/icons";
import { formatPercent, statusText } from "@/lib/format";

/** 单条问答消息：气泡 + 置信度 + 来源引用 + 追问建议 + 反馈（antd Tag 展示状态）。 */
export function MessageItem({
  msg,
  onGiveFeedback,
  onOpenSource,
  onSendSuggestion,
}: {
  msg: DisplayMessage;
  onGiveFeedback: (value: -1 | 1) => void;
  onOpenSource: (documentId: number) => void;
  onSendSuggestion: (text: string) => void;
}) {
  return (
    <div className={`chat-msg ${msg.role === "USER" ? "user" : "assistant"}`}>
      <span className="avatar" style={msg.role === "ASSISTANT" ? { background: "linear-gradient(135deg,var(--violet),var(--primary))" } : undefined}>
        {msg.role === "USER" ? "我" : "AI"}
      </span>
      <div style={{ minWidth: 0 }}>
        <div className="chat-bubble">
          {msg.content}
          {msg.streaming ? <span className="stream-cursor" /> : null}
        </div>

        {msg.role === "ASSISTANT" && !msg.streaming ? (
          <>
            <div className="chat-meta">
              {msg.answerStatus ? <Tag color={statusText("answer", msg.answerStatus)[1]}>{statusText("answer", msg.answerStatus)[0]}</Tag> : null}
              {typeof msg.confidence === "number" ? (
                <span className="confidence-bar">
                  置信度
                  <span className="confidence-track">
                    <span
                      className="confidence-fill"
                      style={{
                        width: `${msg.confidence * 100}%`,
                        background: msg.confidence >= 0.8 ? "var(--success)" : msg.confidence >= 0.6 ? "var(--warning)" : "var(--danger)",
                      }}
                    />
                  </span>
                  {formatPercent(msg.confidence)}
                </span>
              ) : null}
              <button className={`feedback-btn${msg.feedback === 1 ? " active-good" : ""}`} onClick={() => onGiveFeedback(1)} aria-label="有用">
                <Icon name="thumbs-up" size={15} />
              </button>
              <button className={`feedback-btn${msg.feedback === -1 ? " active-bad" : ""}`} onClick={() => onGiveFeedback(-1)} aria-label="无用">
                <Icon name="thumbs-down" size={15} />
              </button>
            </div>

            {msg.sources && msg.sources.length > 0 ? (
              <div className="chat-sources">
                {msg.sources.map((s) => (
                  <div key={s.chunkId} className="chat-source" onClick={() => onOpenSource(s.documentId)}>
                    <Icon name="doc" size={14} />
                    <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
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
