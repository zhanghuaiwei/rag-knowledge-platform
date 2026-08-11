import type {
  DailyUsage,
  DauPoint,
  KnowledgeHealth,
  TokenCost,
  TopDocument,
  UsageAggregation,
} from "@/api-client/types";

/** 用量与质量契约（F2.5）。 */
export interface AnalyticsApi {
  getDailyUsage(params?: { aggregation?: UsageAggregation }): Promise<DailyUsage[]>;
  getTokenCosts(): Promise<TokenCost[]>;
  getTopDocuments(): Promise<TopDocument[]>;
  getDau(): Promise<DauPoint[]>;
  getKnowledgeHealth(): Promise<KnowledgeHealth>;
}
