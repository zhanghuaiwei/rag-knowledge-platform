"use client";

import type { RefObject } from "react";
import { Button, Empty } from "antd";

import { Loading } from "@/components/async-state";
import { MessageItem } from "@/components/chat/message-item";
import type { DisplayMessage } from "@/components/chat/types";

/** 消息流：加载/错误/空态/列表 + 自动滚动锚点。 */
export function ChatMessageList({
  messages,
  loadingMsgs,
  loadError,
  onReload,
  onGiveFeedback,
  onOpenSource,
  onSendSuggestion,
  onRetry,
  bottomRef,
}: {
  messages: DisplayMessage[];
  loadingMsgs: boolean;
  loadError?: boolean;
  onReload?: () => void;
  onGiveFeedback: (message: DisplayMessage, value: -1 | 1) => void;
  onOpenSource: (documentId: number) => void;
  onSendSuggestion: (text: string) => void;
  onRetry?: (message: DisplayMessage) => void;
  bottomRef: RefObject<HTMLDivElement | null>;
}) {
  if (loadingMsgs) return <Loading />;
  if (messages.length === 0) {
    if (loadError) {
      return (
        <Empty
          description={
            <span style={{ color: "var(--danger, #d93026)" }}>
              会话消息加载失败，请检查服务后重试
            </span>
          }
        >
          {onReload ? <Button type="primary" onClick={onReload}>重试</Button> : null}
        </Empty>
      );
    }
    return (
      <Empty
        description={
          <span style={{ color: "var(--text-2)" }}>
            开始提问 · 答案将带来源引用、置信度与新鲜度提示；无权限内容不会出现在回答中
          </span>
        }
      />
    );
  }
  return (
    <>
      {messages.map((m) => (
        <MessageItem
          key={m.id}
          msg={m}
          onGiveFeedback={(v) => onGiveFeedback(m, v)}
          onOpenSource={onOpenSource}
          onSendSuggestion={onSendSuggestion}
          onRetry={onRetry ? () => onRetry(m) : undefined}
        />
      ))}
      <div ref={bottomRef} />
    </>
  );
}
