import type { ChatStreamResult } from "@/api-client";

/** 问答消息展示模型：由 ChatMessage / ChatStreamResult 映射而来。 */
export interface DisplayMessage {
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
