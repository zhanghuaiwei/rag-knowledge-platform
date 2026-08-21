"use client";

import { Button, Input } from "antd";
import { SendOutlined } from "@ant-design/icons";

/** 问答输入区：多行输入 + 发送/停止 + ↑/↓ 翻阅历史提问。 */
export function ChatInput({
  value,
  onChange,
  onSend,
  sending,
  canSend,
  onStop,
  onNavigateHistory,
}: {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  sending: boolean;
  canSend: boolean;
  onStop: () => void;
  /** ↑/↓ 翻阅当前会话历史提问；返回要填充的内容，null 表示无操作。 */
  onNavigateHistory?: (dir: -1 | 1) => string | null;
}) {
  return (
    <div className="chat-input-area">
      <div className="chat-input-wrapper">
        <Input.TextArea
          className="chat-input-textarea"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行…"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              onSend();
              return;
            }
            // ↑/↓ 翻阅历史提问：仅当光标在文本首（↑）/末（↓）时触发，不干扰多行编辑
            const ta = e.target as HTMLTextAreaElement;
            const atStart = ta.selectionStart === 0 && ta.selectionEnd === 0;
            const atEnd = ta.selectionStart === ta.value.length && ta.selectionEnd === ta.value.length;
            if (e.key === "ArrowUp" && atStart) {
              const q = onNavigateHistory?.(-1);
              if (q !== null && q !== undefined) {
                e.preventDefault();
                onChange(q);
              }
            } else if (e.key === "ArrowDown" && atEnd) {
              const q = onNavigateHistory?.(1);
              if (q !== null && q !== undefined) {
                e.preventDefault();
                onChange(q);
              }
            }
          }}
          disabled={sending}
          autoSize={{ minRows: 2, maxRows: 8 }}
          style={{ flex: 1 }}
        />
        <div className="chat-input-footer">
          <span className="chat-input-hint">Enter 发送 · Shift+Enter 换行 · ↑/↓ 翻阅历史提问</span>
        </div>
      </div>
      {sending ? (
        <Button danger onClick={onStop} className="chat-send-btn">
          停止
        </Button>
      ) : (
        <Button
          type="primary"
          icon={<SendOutlined />}
          disabled={!canSend}
          onClick={onSend}
          className="chat-send-btn"
          shape="round"
        >
          发送
        </Button>
      )}
    </div>
  );
}
