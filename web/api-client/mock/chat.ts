import type { ChatApi } from "@/api-client/contracts/chat";
import type {
  ChatFeedbackInput,
  ChatMessage,
  ChatMessageInput,
  ChatSession,
  ChatSource,
  ChatStreamResult,
  CreateChatSessionInput,
  PageParams,
} from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";
import { appendAudit, nextId, now } from "@/mocks/helpers";

function notFound(resource: string): never {
  throw new Error(`${resource}不存在`);
}

export const chatApi: ChatApi = {
  async listChatSessions(params: PageParams = {}) {
    await delay();
    return paginate(db.chatSessions, params.page, params.size);
  },

  async createChatSession(input: CreateChatSessionInput) {
    await delay(200);
    const id = nextId(db.chatSessions);
    const session: ChatSession = {
      id,
      title: input.title?.trim() || (input.kbIds.length ? `知识库问答 ${id}` : "新会话"),
      status: "ACTIVE",
      kbIds: input.kbIds,
      messageCount: 0,
      createdAt: now(),
      updatedAt: now(),
    };
    db.chatSessions.unshift(session);
    db.chatMessagesBySession[id] = [];
    appendAudit({ action: "chat.session.create", resourceType: "CHAT", resourceId: id });
    return session;
  },

  async archiveChatSession(id: number) {
    await delay(200);
    const session = db.chatSessions.find((item) => item.id === id);
    if (!session) notFound("会话");
    session.status = "ARCHIVED";
    session.updatedAt = now();
    return session;
  },

  async listChatMessages(sessionId: number) {
    await delay();
    const messages = db.chatMessagesBySession[sessionId];
    if (!messages) notFound("会话");
    return messages;
  },

  async sendChatMessage(input: ChatMessageInput) {
    await delay(400);
    const session = db.chatSessions.find((item) => item.id === input.sessionId);
    if (!session) notFound("会话");

    const existing = db.chatMessagesBySession[input.sessionId] ?? [];
    const seq = existing.length + 1;

    const sources: ChatSource[] = db.searchItems.slice(0, 2).map((hit) => ({
      documentId: hit.documentId,
      fileName: hit.fileName,
      pageNo: hit.pageNo,
      sectionTitle: hit.sectionTitle,
      chunkId: `mock-chunk-${hit.documentId}`,
      score: hit.score / 20,
    }));

    const content =
      `（mock 回答）关于「${input.content}」：接入真实后端后（NEXT_PUBLIC_USE_MOCK=false），` +
      "此处将返回流式 SSE 结果与真实引用。当前数据来自内置 mock 层。";

    const userMsg: ChatMessage = {
      id: nextId(Object.values(db.chatMessagesBySession).flat()),
      sessionId: input.sessionId,
      seq,
      role: "USER",
      content: input.content,
      answerStatus: null,
      confidence: null,
      feedback: 0,
      tokenIn: 0,
      tokenOut: 0,
      modelName: "",
      sources: [],
      suggestions: [],
      createdAt: now(),
    };
    const assistantMsg: ChatMessage = {
      id: nextId(Object.values(db.chatMessagesBySession).flat()) + 1,
      sessionId: input.sessionId,
      seq: seq + 1,
      role: "ASSISTANT",
      content,
      answerStatus: "ANSWERED",
      confidence: 0.85,
      feedback: 0,
      tokenIn: 120,
      tokenOut: 80,
      modelName: "claude-sonnet-5",
      sources,
      suggestions: ["基于检索来源的追问 1", "基于检索来源的追问 2"],
      createdAt: now(),
    };

    db.chatMessagesBySession[input.sessionId] = [...existing, userMsg, assistantMsg];
    session.messageCount = db.chatMessagesBySession[input.sessionId].length;
    session.updatedAt = now();

    const result: ChatStreamResult = {
      sessionId: input.sessionId,
      messageId: assistantMsg.id,
      answerStatus: "ANSWERED",
      confidence: 0.85,
      content,
      sources,
      suggestions: assistantMsg.suggestions,
      tokenIn: assistantMsg.tokenIn,
      tokenOut: assistantMsg.tokenOut,
      cost: 0.003,
    };
    return result;
  },

  async submitChatFeedback(input: ChatFeedbackInput) {
    await delay(200);
    appendAudit({
      action: `chat.feedback.${input.kind.toLowerCase()}`,
      resourceType: "CHAT_MESSAGE",
      resourceId: input.messageId,
    });
  },
};
