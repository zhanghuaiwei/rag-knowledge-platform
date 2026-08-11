"use client";

import { Button, Input } from "antd";
import { SendOutlined } from "@ant-design/icons";

/** 问答输入区：多行输入 + 发送/停止。 */
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
  return (
    <div className="chat-input-area">
      <Input.TextArea
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
        autoSize={{ minRows: 2, maxRows: 6 }}
        style={{ flex: 1 }}
      />
      {sending ? (
        <Button danger onClick={onStop}>
          停止
        </Button>
      ) : (
        <Button type="primary" icon={<SendOutlined />} disabled={!canSend} onClick={onSend}>
          发送
        </Button>
      )}
    </div>
  );
}
