"use client";

import { Button, Input } from "antd";
import { SendOutlined } from "@ant-design/icons";

/** 问答输入区：多行输入 + 发送/停止 + 字数统计。 */
export function ChatInput({
  value,
  onChange,
  onSend,
  sending,
  canSend,
  onStop,
}: {
  value: string;
  onChange: (value: string) => void;
  onSend: () => void;
  sending: boolean;
  canSend: boolean;
  onStop: () => void;
}) {
  const MAX_LEN = 4000;
  const len = value.length;

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
            }
          }}
          disabled={sending}
          autoSize={{ minRows: 2, maxRows: 8 }}
          style={{ flex: 1 }}
        />
        <div className="chat-input-footer">
          <span className={`chat-input-count ${len > MAX_LEN * 0.9 ? "near-limit" : ""}`}>
            {len}/{MAX_LEN}
          </span>
          <span className="chat-input-hint">Enter 发送 · Shift+Enter 换行</span>
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
