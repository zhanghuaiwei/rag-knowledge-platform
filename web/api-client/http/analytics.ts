/**
 * 用量与质量域真实 HTTP transport（对齐 OpenAPI analytics + 产品契约新增端点）。
 */
import type { AnalyticsApi } from "@/api-client/contracts/analytics";
import type {
  DailyUsage,
  DauPoint,
  KnowledgeHealth,
  TokenCost,
  TopDocument,
  UsageAggregation,
} from "@/api-client/types";
import { request } from "@/api-client/http/client";

export const analyticsApi: AnalyticsApi = {
  async getDailyUsage(params?: { aggregation?: UsageAggregation }) {
    return request<DailyUsage[]>({
      method: "GET",
      url: "/analytics/usage",
      params: { period: params?.aggregation ?? "DAY" },
    });
  },

  async getTokenCosts() {
    return request<TokenCost[]>({
      method: "GET",
      url: "/analytics/costs",
      params: { period: "DAY" },
    });
  },

  async getTopDocuments() {
    return request<TopDocument[]>({ method: "GET", url: "/analytics/top-documents" });
  },

  async getDau() {
    return request<DauPoint[]>({ method: "GET", url: "/analytics/dau" });
  },

  async getKnowledgeHealth() {
    return request<KnowledgeHealth>({ method: "GET", url: "/analytics/kb-health" });
  },
};
