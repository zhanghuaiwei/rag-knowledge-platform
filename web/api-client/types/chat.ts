/** 问答会话域类型。 */
import type { PageParams } from "@/api-client/types/common";

export interface ChatSession {
  id: number;
  title: string;
  status: "ACTIVE" | "ARCHIVED";
  kbIds: number[];
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export type AnswerStatus =
  | "ANSWERED"
  | "NO_ANSWER"
  | "LOW_CONFIDENCE"
  | "BLOCKED";

export interface ChatSource {
  documentId: number;
  fileName: string;
  pageNo: number;
  sectionTitle: string;
  chunkId: string;
  score: number;
}

export interface ChatMessage {
  id: number;
  sessionId: number;
  seq: number;
  role: "USER" | "ASSISTANT";
  content: string;
  answerStatus: AnswerStatus | null;
  confidence: number | null;
  feedback: -1 | 0 | 1;
  tokenIn: number;
  tokenOut: number;
  modelName: string;
  sources: ChatSource[];
  suggestions: string[];
  createdAt: string;
}

export interface ChatMessageInput {
  sessionId: number;
  content: string;
  kbIds: number[];
}

/** 新会话入参：真实实现由服务端创建后返回 id。 */
export interface CreateChatSessionInput {
  title?: string;
  kbIds: number[];
}

/** 问答反馈（错误/过期/无权限/引用不符）。 */
export interface ChatFeedbackInput {
  messageId: number;
  kind: "WRONG" | "STALE" | "NO_PERMISSION" | "CITATION";
  note?: string;
}

export interface ChatStreamResult {
  sessionId: number;
  messageId: number;
  answerStatus: AnswerStatus;
  confidence: number;
  content: string;
  sources: ChatSource[];
  suggestions: string[];
  tokenIn: number;
  tokenOut: number;
  cost: number;
}
