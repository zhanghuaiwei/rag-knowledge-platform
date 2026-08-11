import type {
  ChatFeedbackInput,
  ChatMessage,
  ChatMessageInput,
  ChatSession,
  ChatStreamResult,
  CreateChatSessionInput,
  PageParams,
  PageResult,
} from "@/api-client/types";

/** 智能问答契约（F2.3）。 */
export interface ChatApi {
  listChatSessions(params?: PageParams): Promise<PageResult<ChatSession>>;
  createChatSession(input: CreateChatSessionInput): Promise<ChatSession>;
  archiveChatSession(id: number): Promise<ChatSession>;
  listChatMessages(sessionId: number): Promise<ChatMessage[]>;
  sendChatMessage(input: ChatMessageInput): Promise<ChatStreamResult>;
  submitChatFeedback(input: ChatFeedbackInput): Promise<void>;
}
