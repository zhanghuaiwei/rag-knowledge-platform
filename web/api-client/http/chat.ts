/**
 * 智能问答域真实 HTTP transport（对齐 OpenAPI search-chat）。
 * 提问走 SSE：meta → token* → sources? → final，transport 内聚合并返回完整结果
 * （保持前端契约 `Promise<ChatStreamResult>` 不变）。
 */
import type { ChatApi } from "@/api-client/contracts/chat";
import type {
  AnswerStatus,
  ChatFeedbackInput,
  ChatMessage,
  ChatMessageInput,
  ChatSession,
  ChatSource,
  ChatStreamResult,
  CreateChatSessionInput,
  PageParams,
  PageResult,
} from "@/api-client/types";
import { buildApiUrl, request, requestVoid } from "@/api-client/http/client";
import { readSse } from "@/api-client/http/sse";
import { getAccessToken } from "@/lib/auth";

/** 反馈 kind（均为踩时原因）→ 后端 reaction。 */
const KIND_TO_REACTION: Record<ChatFeedbackInput["kind"], "up" | "down"> = {
  WRONG: "down",
  STALE: "down",
  NO_PERMISSION: "down",
  CITATION: "down",
};

function parseEventData(raw: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(raw) as unknown;
    return typeof parsed === "object" && parsed != null ? (parsed as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

export const chatApi: ChatApi = {
  async listChatSessions(params: PageParams = {}) {
    return request<PageResult<ChatSession>>({
      method: "GET",
      url: "/chats",
      params: { page: params.page ?? 1, size: params.size ?? 20 },
    });
  },

  async createChatSession(input: CreateChatSessionInput) {
    return request<ChatSession>({ method: "POST", url: "/chats", data: input });
  },

  async archiveChatSession(id: number) {
    return request<ChatSession>({ method: "POST", url: `/chats/${id}/archive` });
  },

  async listChatMessages(sessionId: number) {
    const page = await request<{ items?: ChatMessage[] }>({
      method: "GET",
      url: `/chats/${sessionId}`,
    });
    return page.items ?? [];
  },

  async sendChatMessage(input: ChatMessageInput) {
    return askStream(input);
  },

  async submitChatFeedback(input: ChatFeedbackInput) {
    await requestVoid({
      method: "POST",
      url: `/messages/${input.messageId}/feedback`,
      data: { reaction: KIND_TO_REACTION[input.kind], reason: input.note },
    });
  },
};

async function askStream(input: ChatMessageInput): Promise<ChatStreamResult> {
  let content = "";
  let messageId = 0;
  let answerStatus: AnswerStatus = "ANSWERED";
  let confidence = 0;
  let sources: ChatSource[] = [];
  let suggestions: string[] = [];
  let tokenIn = 0;
  let tokenOut = 0;
  let cost = 0;

  const token = getAccessToken();
  await readSse(
    buildApiUrl(`/chats/${input.sessionId}/messages`),
    {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ question: input.content, memoryTurns: 10, stream: true }),
    },
    (event) => {
      const data = parseEventData(event.data);
      switch (event.event) {
        case "meta":
          if (typeof data.messageId === "number") messageId = data.messageId;
          break;
        case "token":
          if (typeof data.text === "string") content += data.text;
          else if (typeof data.content === "string") content += data.content;
          break;
        case "sources":
          if (Array.isArray(data.sources)) sources = data.sources as ChatSource[];
          break;
        case "final":
          if (typeof data.messageId === "number") messageId = data.messageId;
          if (typeof data.answerStatus === "string") answerStatus = data.answerStatus as AnswerStatus;
          if (typeof data.confidence === "number") confidence = data.confidence;
          if (typeof data.content === "string") content = data.content;
          if (Array.isArray(data.sources)) sources = data.sources as ChatSource[];
          if (Array.isArray(data.suggestions)) suggestions = data.suggestions as string[];
          if (typeof data.tokenIn === "number") tokenIn = data.tokenIn;
          if (typeof data.tokenOut === "number") tokenOut = data.tokenOut;
          if (typeof data.cost === "number") cost = data.cost;
          break;
        default:
          break;
      }
    },
  );

  return {
    sessionId: input.sessionId,
    messageId,
    answerStatus,
    confidence,
    content,
    sources,
    suggestions,
    tokenIn,
    tokenOut,
    cost,
  };
}
