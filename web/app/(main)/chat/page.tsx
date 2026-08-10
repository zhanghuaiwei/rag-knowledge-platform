"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { Button, Card, Drawer, Empty, Input, Tag } from "antd";
import { MenuOutlined, SendOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { ChatMessage, ChatSession, ChatStreamResult } from "@/api-client";
import { Loading } from "@/components/async-state";
import { MessageItem } from "@/components/chat/message-item";
import { SessionList } from "@/components/chat/session-list";
import type { DisplayMessage } from "@/components/chat/types";
import { ChatFeedbackModal } from "@/components/chat-feedback-modal";
import { useToast } from "@/components/feedback";
import { useAsync } from "@/lib/use-async";

function ChatPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const toast = useToast();
  const sessions = useAsync(() => api.listChatSessions({ page: 1, size: 30 }));

  const [activeId, setActiveId] = useState<number | null>(null);
  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [loadingMsgs, setLoadingMsgs] = useState(false);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [feedbackTarget, setFeedbackTarget] = useState<DisplayMessage | null>(null);
  const [feedbackType, setFeedbackType] = useState("WRONG");
  const [feedbackNote, setFeedbackNote] = useState("");
  const [mobileSessionsOpen, setMobileSessionsOpen] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const streamTimer = useRef<ReturnType<typeof setInterval> | null>(null);
  // 从知识库详情「基于此库问答」带入的库范围（新会话生效）
  const scopeKbId = Number(searchParams.get("kb")) || null;
  // 初始化会话选择（支持 ?session= 直达）
  useEffect(() => {
    if (!sessions.data) return;
    const fromUrl = Number(searchParams.get("session"));
    const first = sessions.data.items[0]?.id ?? null;
    setActiveId(sessions.data.items.some((s) => s.id === fromUrl) ? fromUrl : first);
  }, [sessions.data, searchParams]);
  // 加载会话消息
  useEffect(() => {
    if (activeId === null) return;
    let cancelled = false;
    setLoadingMsgs(true);
    api
      .listChatMessages(activeId)
      .then((items: ChatMessage[]) => {
        if (cancelled) return;
        setMessages(
          items.map((m) => ({
            id: m.id,
            role: m.role,
            content: m.content,
            answerStatus: m.answerStatus,
            confidence: m.confidence,
            sources: m.sources,
            suggestions: m.suggestions,
            feedback: m.feedback,
          })),
        );
      })
      .catch(() => {
        if (!cancelled) {
          setMessages([]);
          toast("error", "会话消息加载失败，请切换会话重试");
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingMsgs(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeId, toast]);
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);
  useEffect(
    () => () => {
      if (streamTimer.current) clearInterval(streamTimer.current);
    },
    [],
  );
  const activeSession: ChatSession | undefined = sessions.data?.items.find((s) => s.id === activeId);
  const stopStreaming = () => {
    if (streamTimer.current) {
      clearInterval(streamTimer.current);
      streamTimer.current = null;
    }
    setMessages((prev) => prev.map((m) => (m.streaming ? { ...m, streaming: false, content: `${m.content}\n\n（已停止生成）` } : m)));
    setSending(false);
  };
  /** 模拟 SSE token 流：接入真实后端后由 api-client 的 SSE 封装驱动（契约待冻结）。 */
  const streamAnswer = (result: ChatStreamResult) => {
    const full = result.content;
    let cursor = 0;
    streamTimer.current = setInterval(() => {
      cursor += 2 + Math.floor(Math.random() * 3);
      const done = cursor >= full.length;
      setMessages((prev) =>
        prev.map((m) =>
          m.id === result.messageId
            ? {
                ...m,
                content: full.slice(0, cursor),
                streaming: !done,
                ...(done ? { sources: result.sources, suggestions: result.suggestions } : {}),
              }
            : m,
        ),
      );
      if (done && streamTimer.current) {
        clearInterval(streamTimer.current);
        streamTimer.current = null;
        setSending(false);
      }
    }, 30);
  };
  const send = async (raw?: string) => {
    const content = (raw ?? input).trim();
    if (!content || sending) return;
    setSending(true);
    setInput("");

    let sessionId = activeId;
    if (sessionId === null) {
      // 本地新建会话（mock）；真实实现由服务端创建会话后返回 id
      sessionId = Date.now();
      setActiveId(sessionId);
      setMessages([]);
    }

    const userMsg: DisplayMessage = { id: Date.now() + 1, role: "USER", content };
    const placeholder: DisplayMessage = { id: Date.now() + 2, role: "ASSISTANT", content: "", streaming: true };
    setMessages((prev) => [...prev, userMsg, placeholder]);

    try {
      const result = await api.sendChatMessage({
        sessionId,
        content,
        kbIds: activeSession?.kbIds ?? (scopeKbId ? [scopeKbId] : []),
      });
      setMessages((prev) =>
        prev.map((m) =>
          m.id === placeholder.id
            ? { ...m, id: result.messageId, answerStatus: result.answerStatus, confidence: result.confidence, feedback: 0 }
            : m,
        ),
      );
      streamAnswer(result);
    } catch {
      // mock 层对本地新会话会 notFound：降级为本地演示回答
      const fallback: ChatStreamResult = {
        sessionId,
        messageId: placeholder.id,
        answerStatus: "ANSWERED",
        confidence: 0.72,
        content: `（mock 回答）已收到你的问题「${content}」。接入真实后端后，这里将基于授权范围内的知识库生成带引用的流式回答。`,
        sources: [],
        suggestions: ["换一个角度追问", "缩小知识库范围"],
        tokenIn: 0,
        tokenOut: 0,
        cost: 0,
      };
      setMessages((prev) =>
        prev.map((m) => (m.id === placeholder.id ? { ...m, answerStatus: fallback.answerStatus, confidence: fallback.confidence, feedback: 0 } : m)),
      );
      streamAnswer(fallback);
    }
  };
  const newSession = () => {
    stopStreaming();
    setActiveId(null);
    setMessages([]);
    router.replace("/chat");
  };
  const giveFeedback = (msg: DisplayMessage, value: -1 | 1) => {
    setMessages((prev) => prev.map((m) => (m.id === msg.id ? { ...m, feedback: m.feedback === value ? 0 : value } : m)));
    if (value === -1 && msg.feedback !== -1) {
      setFeedbackType("WRONG");
      setFeedbackNote("");
      setFeedbackTarget(msg);
    } else {
      toast("success", "感谢反馈，已记录用于质量评估");
    }
  };
  const submitFeedback = () => {
    if (!feedbackTarget) return;
    // mock：真实环境提交 messageId + 类型 + 说明到反馈接口（契约待冻结）
    toast("success", `反馈已提交（${feedbackType}${feedbackNote.trim() ? "，含补充说明" : ""}），将进入质量评估闭环`);
    setFeedbackTarget(null);
  };
  const sessionList = (
    <SessionList
      sessions={sessions.data?.items ?? []}
      loading={sessions.loading}
      activeId={activeId}
      onSelect={(id) => {
        stopStreaming();
        setActiveId(id);
        setMobileSessionsOpen(false);
      }}
      onNew={() => {
        newSession();
        setMobileSessionsOpen(false);
      }}
    />
  );
  return (
    <div className="chat-layout">
      <Card className="chat-sessions" style={{ padding: 12 }}>
        {sessionList}
      </Card>
      <Card className="chat-main" styles={{ body: { display: "flex", flexDirection: "column", height: "100%", padding: 16 } }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap", paddingBottom: 12, borderBottom: "1px solid var(--border)", marginBottom: 12 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, minWidth: 0 }}>
            <Button type="text" className="chat-sessions-toggle" icon={<MenuOutlined />} aria-label="会话列表" onClick={() => setMobileSessionsOpen(true)} />
            <div style={{ minWidth: 0 }}>
              <strong>{activeSession?.title ?? "新会话"}</strong>
              <span style={{ color: "var(--text-3)", fontSize: 12, marginLeft: 10 }}>
                范围：{activeSession?.kbIds.length ? `${activeSession.kbIds.length} 个知识库` : scopeKbId ? "当前知识库" : "全部可访问知识库"}
              </span>
            </div>
          </div>
          <Tag color="processing">回答仅基于你有权限的内容</Tag>
        </div>
        <div className="chat-messages" style={{ flex: 1, overflowY: "auto" }}>
          {loadingMsgs ? (
            <Loading />
          ) : messages.length === 0 ? (
            <Empty description={<span style={{ color: "var(--text-2)" }}>开始提问 · 答案将带来源引用、置信度与新鲜度提示；无权限内容不会出现在回答中</span>} />
          ) : (
            messages.map((m) => (
              <MessageItem
                key={m.id}
                msg={m}
                onGiveFeedback={(v) => giveFeedback(m, v)}
                onOpenSource={(docId) => router.push(`/documents/${docId}`)}
                onSendSuggestion={(text) => void send(text)}
              />
            ))
          )}
          <div ref={bottomRef} />
        </div>

        <div className="chat-input-area">
          <Input.TextArea
            placeholder="输入问题，Enter 发送，Shift+Enter 换行…"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
            disabled={sending && !streamTimer.current}
            autoSize={{ minRows: 2, maxRows: 6 }}
            style={{ flex: 1 }}
          />
          {sending ? (
            <Button danger onClick={stopStreaming}>停止</Button>
          ) : (
            <Button type="primary" icon={<SendOutlined />} disabled={!input.trim()} onClick={() => void send()}>
              发送
            </Button>
          )}
        </div>
      </Card>

      <Drawer title="会话列表" open={mobileSessionsOpen} onClose={() => setMobileSessionsOpen(false)} placement="left" width={280}>
        {sessionList}
      </Drawer>

      <ChatFeedbackModal
        open={feedbackTarget !== null}
        type={feedbackType}
        note={feedbackNote}
        onTypeChange={setFeedbackType}
        onNoteChange={setFeedbackNote}
        onClose={() => setFeedbackTarget(null)}
        onSubmit={submitFeedback}
      />
    </div>
  );
}

export default function ChatPage() {
  return (
    <Suspense fallback={<Loading />}>
      <ChatPageInner />
    </Suspense>
  );
}
