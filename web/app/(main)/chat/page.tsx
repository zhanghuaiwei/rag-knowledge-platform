"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";

import { api } from "@/api-client";
import type { ChatMessage, ChatSession, ChatStreamResult } from "@/api-client";
import { Icon } from "@/components/icons";
import { Drawer, Empty, Loading, Modal, Tag, useToast } from "@/components/ui";
import { formatPercent, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

interface DisplayMessage {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  streaming?: boolean;
  answerStatus?: string | null;
  confidence?: number | null;
  sources?: ChatStreamResult["sources"];
  suggestions?: string[];
  feedback?: -1 | 0 | 1;
}

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
    <>
      <button
        className="btn btn-primary btn-block"
        style={{ marginBottom: 10 }}
        onClick={() => {
          newSession();
          setMobileSessionsOpen(false);
        }}
      >
        <Icon name="plus" size={15} /> 新建会话
      </button>
      {sessions.loading ? (
        <Loading />
      ) : (
        <>
          {activeId !== null && !sessions.data?.items.some((s) => s.id === activeId) ? (
            <div className="chat-session-item active">
              <div className="t">新会话</div>
              <div className="s">未保存</div>
            </div>
          ) : null}
          {sessions.data?.items.map((s) => (
            <div
              key={s.id}
              className={`chat-session-item${s.id === activeId ? " active" : ""}`}
              onClick={() => {
                stopStreaming();
                setActiveId(s.id);
                setMobileSessionsOpen(false);
              }}
            >
              <div className="t">{s.title}</div>
              <div className="s">{s.messageCount} 条 · {formatRelative(s.updatedAt)}</div>
            </div>
          ))}
        </>
      )}
    </>
  );

  return (
    <div className="chat-layout">
      <div className="card chat-sessions" style={{ padding: 12 }}>
        {sessionList}
      </div>

      <div className="card chat-main">
        <div style={{ borderBottom: "1px solid var(--border)", paddingBottom: 12, marginBottom: 12, display: "flex", justifyContent: "space-between", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, minWidth: 0 }}>
            <button className="icon-btn chat-sessions-toggle" onClick={() => setMobileSessionsOpen(true)} aria-label="会话列表" title="会话列表">
              <Icon name="menu" />
            </button>
            <div style={{ minWidth: 0 }}>
              <strong>{activeSession?.title ?? "新会话"}</strong>
              <span style={{ color: "var(--text-3)", fontSize: 12, marginLeft: 10 }}>
                范围：{activeSession?.kbIds.length ? `${activeSession.kbIds.length} 个知识库` : scopeKbId ? "当前知识库" : "全部可访问知识库"}
              </span>
            </div>
          </div>
          <Tag color="info">回答仅基于你有权限的内容</Tag>
        </div>

        <div className="chat-messages">
          {loadingMsgs ? (
            <Loading />
          ) : messages.length === 0 ? (
            <Empty
              icon="💬"
              title="开始提问"
              desc="答案将带来源引用、置信度与新鲜度提示；无权限内容不会出现在回答中"
            />
          ) : (
            messages.map((m) => (
              <div key={m.id} className={`chat-msg ${m.role === "USER" ? "user" : "assistant"}`}>
                <span className="avatar" style={m.role === "ASSISTANT" ? { background: "linear-gradient(135deg,var(--violet),var(--primary))" } : undefined}>
                  {m.role === "USER" ? "我" : "AI"}
                </span>
                <div style={{ minWidth: 0 }}>
                  <div className="chat-bubble">
                    {m.content}
                    {m.streaming ? <span className="stream-cursor" /> : null}
                  </div>

                  {m.role === "ASSISTANT" && !m.streaming ? (
                    <>
                      <div className="chat-meta">
                        {m.answerStatus ? <Tag color={statusText("answer", m.answerStatus)[1]}>{statusText("answer", m.answerStatus)[0]}</Tag> : null}
                        {typeof m.confidence === "number" ? (
                          <span className="confidence-bar">
                            置信度
                            <span className="confidence-track">
                              <span
                                className="confidence-fill"
                                style={{
                                  width: `${m.confidence * 100}%`,
                                  background: m.confidence >= 0.8 ? "var(--success)" : m.confidence >= 0.6 ? "var(--warning)" : "var(--danger)",
                                }}
                              />
                            </span>
                            {formatPercent(m.confidence)}
                          </span>
                        ) : null}
                        <button className={`feedback-btn${m.feedback === 1 ? " active-good" : ""}`} onClick={() => giveFeedback(m, 1)} aria-label="有用">
                          <Icon name="thumbs-up" size={15} />
                        </button>
                        <button className={`feedback-btn${m.feedback === -1 ? " active-bad" : ""}`} onClick={() => giveFeedback(m, -1)} aria-label="无用">
                          <Icon name="thumbs-down" size={15} />
                        </button>
                      </div>

                      {m.sources && m.sources.length > 0 ? (
                        <div className="chat-sources">
                          {m.sources.map((s) => (
                            <div key={s.chunkId} className="chat-source" onClick={() => router.push(`/documents/${s.documentId}`)}>
                              <Icon name="doc" size={14} />
                              <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                {s.fileName} · 第 {s.pageNo} 页 · {s.sectionTitle}
                              </span>
                              <Tag>{formatPercent(s.score)}</Tag>
                            </div>
                          ))}
                        </div>
                      ) : null}

                      {m.suggestions && m.suggestions.length > 0 ? (
                        <div className="chat-suggestions">
                          {m.suggestions.map((sug) => (
                            <button key={sug} className="chat-suggestion" onClick={() => send(sug)}>
                              {sug}
                            </button>
                          ))}
                        </div>
                      ) : null}
                    </>
                  ) : null}
                </div>
              </div>
            ))
          )}
          <div ref={bottomRef} />
        </div>

        <div className="chat-input-area">
          <textarea
            className="textarea"
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
          />
          {sending ? (
            <button className="btn btn-danger" onClick={stopStreaming}>
              停止
            </button>
          ) : (
            <button className="btn btn-primary" onClick={() => void send()} disabled={!input.trim()}>
              <Icon name="send" size={15} /> 发送
            </button>
          )}
        </div>
      </div>

      <Drawer title="会话列表" open={mobileSessionsOpen} onClose={() => setMobileSessionsOpen(false)}>
        {sessionList}
      </Drawer>

      <Modal
        title="反馈问题"
        open={feedbackTarget !== null}
        onClose={() => setFeedbackTarget(null)}
        footer={
          <>
            <button className="btn" onClick={() => setFeedbackTarget(null)}>取消</button>
            <button className="btn btn-primary" onClick={submitFeedback}>
              提交反馈
            </button>
          </>
        }
      >
        <div className="field">
          <label className="field-label">问题类型</label>
          <select className="select" value={feedbackType} onChange={(e) => setFeedbackType(e.target.value)}>
            <option value="WRONG">内容错误</option>
            <option value="STALE">信息过期</option>
            <option value="NO_PERMISSION">引用无权限</option>
            <option value="CITATION">引用不符</option>
          </select>
        </div>
        <div className="field" style={{ marginBottom: 0 }}>
          <label className="field-label">补充说明（可选）</label>
          <textarea
            className="textarea"
            placeholder="描述你遇到的问题…"
            value={feedbackNote}
            maxLength={200}
            onChange={(e) => setFeedbackNote(e.target.value)}
          />
        </div>
      </Modal>
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
