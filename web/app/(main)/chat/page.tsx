"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { Card, Drawer } from "antd";

import { api } from "@/api-client";
import type { ChatMessage, ChatSession } from "@/api-client";
import { Loading } from "@/components/async-state";
import { ChatHeader } from "@/components/chat/chat-header";
import { ChatInput } from "@/components/chat/chat-input";
import { ChatMessageList } from "@/components/chat/message-list";
import { ScopePicker } from "@/components/chat/scope-picker";
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
  const [loadError, setLoadError] = useState(false);
  // 消息加载失败后的手动重试标记（变更触发重新拉取当前会话消息）
  const [reloadTick, setReloadTick] = useState(0);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [feedbackTarget, setFeedbackTarget] = useState<DisplayMessage | null>(null);
  const [feedbackType, setFeedbackType] = useState("WRONG");
  const [feedbackNote, setFeedbackNote] = useState("");
  const [mobileSessionsOpen, setMobileSessionsOpen] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  // UI 级取消标记：transport 内部 SSE 聚合无暴露 abort，点击停止后丢弃待返回结果
  const cancelledRef = useRef(false);
  // 从知识库详情「基于此库问答」带入的库范围（新会话生效）
  const scopeKbId = Number(searchParams.get("kb")) || null;
  // 新会话知识库范围（多选，最多 5 个；F2.1 用例步骤 3）
  const [newScopeIds, setNewScopeIds] = useState<number[]>(scopeKbId ? [scopeKbId] : []);
  const [scopePickerOpen, setScopePickerOpen] = useState(false);
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
    setLoadError(false);
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
          // 保留错误态（Empty 中提供重试入口），toast 仅瞬时提示
          setLoadError(true);
          toast("error", "会话消息加载失败，请稍后重试");
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingMsgs(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeId, toast, reloadTick]);
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);
  const activeSession: ChatSession | undefined = sessions.data?.items.find((s) => s.id === activeId);

  // UI 级停止：只复位发送态并标记丢弃待返回结果（真实 SSE 在 transport 内聚合，无页面级 abort）
  const stopStreaming = () => {
    cancelledRef.current = true;
    setSending(false);
    setMessages((prev) => prev.map((m) => (m.streaming ? { ...m, streaming: false } : m)));
  };

  // 核心发送逻辑：支持新会话创建 + 已有会话追加
  const send = async (raw?: string, retryMessageId?: number) => {
    const content = (raw ?? input).trim();
    if (!content || sending) return;
    setSending(true);
    setInput("");
    cancelledRef.current = false;

    let sessionId = activeId;
    // 查找对应的用户消息（重试时使用原问题）
    let userMessageIdForRetry: number | null = null;

    if (sessionId === null) {
      // 新会话：按用户选择的知识库范围创建真实会话；失败如实报错，不再降级为本地会话
      try {
        const created = await api.createChatSession({ kbIds: newScopeIds });
        sessionId = created.id;
        setActiveId(sessionId);
        setMessages([]);
        sessions.reload();
      } catch (err: unknown) {
        setSending(false);
        toast("error", err instanceof Error ? err.message : "创建会话失败，请重试");
        return;
      }
    }

    let placeholderId: number;

    if (retryMessageId !== undefined) {
      // 重试场景：复用原问题，替换失败的 assistant 消息
      const retryUserMsg = messages.find((m) => m.id === retryMessageId);
      if (!retryUserMsg) {
        setSending(false);
        return;
      }
      // 找到失败的 assistant 消息（紧跟在用户消息后面）
      const msgIdx = messages.findIndex((m) => m.id === retryMessageId);
      const assistantIdx = msgIdx + 1;
      const placeholder: DisplayMessage = {
        id: Date.now(),
        role: "ASSISTANT",
        content: "",
        streaming: true,
      };
      placeholderId = placeholder.id;
      // 替换原失败消息为 placeholder
      setMessages((prev) => {
        const next = [...prev];
        if (assistantIdx < next.length && next[assistantIdx].role === "ASSISTANT") {
          next[assistantIdx] = placeholder;
        } else {
          next.push(placeholder);
        }
        return next;
      });
    } else {
      // 正常发送：添加用户消息 + placeholder
      const userMsg: DisplayMessage = { id: Date.now() + 1, role: "USER", content };
      const placeholder: DisplayMessage = { id: Date.now() + 2, role: "ASSISTANT", content: "", streaming: true };
      placeholderId = placeholder.id;
      userMessageIdForRetry = userMsg.id;
      setMessages((prev) => [...prev, userMsg, placeholder]);
    }

    try {
      const result = await api.sendChatMessage({
        sessionId,
        content,
        kbIds: activeSession?.kbIds ?? newScopeIds,
      });
      if (cancelledRef.current) {
        // 已点击停止：丢弃待返回结果
        setMessages((prev) => prev.filter((m) => m.id !== placeholderId));
        return;
      }
      // transport 内已聚合完整 SSE（meta → token → sources → final），结果到达后一次性真实展示
      setMessages((prev) =>
        prev.map((m) =>
          m.id === placeholderId
            ? {
                ...m,
                id: result.messageId,
                content: result.content,
                streaming: false,
                answerStatus: result.answerStatus,
                confidence: result.confidence,
                sources: result.sources,
                suggestions: result.suggestions,
                feedback: 0,
              }
            : m,
        ),
      );
      // 更新会话标题（首次回答后后端会生成标题）
      if (sessions.data) {
        const updatedSession = sessions.data.items.find((s) => s.id === sessionId);
        if (updatedSession && (updatedSession.title === "新会话" || !updatedSession.title)) {
          sessions.reload();
        }
      }
    } catch (err: unknown) {
      if (cancelledRef.current) return;
      setMessages((prev) =>
        prev.map((m) => (m.id === placeholderId ? { ...m, content: "回答生成失败，请重试", streaming: false, answerStatus: "BLOCKED" } : m)),
      );
      toast("error", err instanceof Error ? err.message : "回答生成失败");
    } finally {
      if (!cancelledRef.current) setSending(false);
    }
  };

  // 重试失败的消息：找到对应的用户问题并重新发送
  const retryMessage = (failedMsg: DisplayMessage) => {
    // 找到该 assistant 消息前面的用户消息
    const idx = messages.findIndex((m) => m.id === failedMsg.id);
    // 向前找最近的 USER 消息
    let userMsgId: number | null = null;
    for (let i = idx - 1; i >= 0; i--) {
      if (messages[i].role === "USER") {
        userMsgId = messages[i].id;
        break;
      }
    }
    if (userMsgId !== null) {
      const userMsg = messages.find((m) => m.id === userMsgId);
      if (userMsg) {
        void send(userMsg.content, userMsgId);
      }
    } else {
      toast("error", "找不到对应的问题");
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
  const submitFeedback = async () => {
    if (!feedbackTarget) return;
    try {
      await api.submitChatFeedback({
        messageId: feedbackTarget.id,
        kind: feedbackType as "WRONG" | "STALE" | "NO_PERMISSION" | "CITATION",
        note: feedbackNote.trim() || undefined,
      });
      toast("success", "反馈已提交，将进入质量评估闭环");
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "反馈提交失败");
    } finally {
      setFeedbackTarget(null);
    }
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
        <ChatHeader
          title={activeSession?.title ?? "新会话"}
          scopeLabel={
            activeSession?.kbIds.length
              ? `${activeSession.kbIds.length} 个知识库`
              : newScopeIds.length
                ? `${newScopeIds.length} 个知识库`
                : "全部可访问知识库"
          }
          onOpenSessions={() => setMobileSessionsOpen(true)}
          onOpenScope={() => setScopePickerOpen(true)}
        />
        <div className="chat-messages" style={{ flex: 1, overflowY: "auto" }}>
          <ChatMessageList
            messages={messages}
            loadingMsgs={loadingMsgs}
            loadError={loadError}
            onReload={() => setReloadTick((t) => t + 1)}
            onGiveFeedback={giveFeedback}
            onOpenSource={(docId) => router.push(`/documents/${docId}`)}
            onSendSuggestion={(text) => void send(text)}
            onRetry={retryMessage}
            bottomRef={bottomRef}
          />
        </div>

        <ChatInput
          value={input}
          onChange={setInput}
          onSend={() => void send()}
          sending={sending}
          canSend={input.trim().length > 0}
          onStop={stopStreaming}
        />
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

      <ScopePicker
        open={scopePickerOpen}
        value={newScopeIds}
        onClose={() => setScopePickerOpen(false)}
        onConfirm={setNewScopeIds}
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
