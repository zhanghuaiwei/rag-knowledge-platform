import type { AnalyticsApi } from "@/api-client/contracts/analytics";
import type { DailyUsage, UsageAggregation } from "@/api-client/types";
import { db, delay } from "@/mocks/db";

/** 按日聚合 → 周/月（周按 ISO 周标签，月按 yyyy-MM），缺失字段补零。 */
function aggregate(usage: DailyUsage[], aggregation: UsageAggregation): DailyUsage[] {
  if (aggregation === "DAY" || usage.length === 0) return usage;
  const groups = new Map<string, DailyUsage[]>();
  for (const point of usage) {
    const date = new Date(point.date);
    let key = point.date.slice(0, 7); // yyyy-MM
    if (aggregation === "WEEK") {
      const day = (date.getUTCDay() + 6) % 7; // 周一为一周起点
      const monday = new Date(date.getTime() - day * 86_400_000).toISOString().slice(0, 10);
      key = `W${monday}`;
    }
    const list = groups.get(key) ?? [];
    list.push(point);
    groups.set(key, list);
  }
  const keys = [...groups.keys()].sort();
  return keys.map((key) => {
    const points = groups.get(key) ?? [];
    return points.reduce<DailyUsage>(
      (acc, p) => ({
        date: key,
        searchCount: acc.searchCount + p.searchCount,
        qaCount: acc.qaCount + p.qaCount,
        noAnswerCount: acc.noAnswerCount + p.noAnswerCount,
        lowConfCount: acc.lowConfCount + p.lowConfCount,
        tokenIn: acc.tokenIn + p.tokenIn,
        tokenOut: acc.tokenOut + p.tokenOut,
        activeUsers: Math.max(acc.activeUsers, p.activeUsers),
        cost: acc.cost + p.cost,
      }),
      { date: key, searchCount: 0, qaCount: 0, noAnswerCount: 0, lowConfCount: 0, tokenIn: 0, tokenOut: 0, activeUsers: 0, cost: 0 },
    );
  });
}

export const analyticsApi: AnalyticsApi = {
  async getDailyUsage(params: { aggregation?: UsageAggregation } = {}) {
    await delay();
    return aggregate(db.dailyUsage, params.aggregation ?? "DAY");
  },
  async getTokenCosts() {
    await delay();
    return db.tokenCosts;
  },
  async getTopDocuments() {
    await delay();
    return db.topDocuments;
  },
  async getDau() {
    await delay();
    return db.dau;
  },
  async getKnowledgeHealth() {
    await delay();
    return db.knowledgeHealth;
  },
};
