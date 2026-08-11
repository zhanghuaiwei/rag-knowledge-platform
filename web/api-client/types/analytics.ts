/** 用量与质量域类型（F2.5 / GKB-09）。 */

export type UsageAggregation = "DAY" | "WEEK" | "MONTH";

export interface DailyUsage {
  date: string;
  searchCount: number;
  qaCount: number;
  noAnswerCount: number;
  lowConfCount: number;
  tokenIn: number;
  tokenOut: number;
  activeUsers: number;
  cost: number;
}

export interface TokenCost {
  modelName: string;
  tokenIn: number;
  tokenOut: number;
  cost: number;
  calls: number;
}

export interface TopDocument {
  documentId: number;
  fileName: string;
  kbName: string;
  qaCount: number;
  searchCount: number;
}

export interface DauPoint {
  date: string;
  activeUsers: number;
}

export interface KnowledgeHealth {
  noAnswerRate: number;
  lowConfRate: number;
  averageConfidence: number;
  freshnessScore: number;
}
