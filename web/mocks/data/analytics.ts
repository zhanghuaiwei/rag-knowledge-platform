/**
 * mock 用量与质量数据（对齐 usage_daily / cost_record / 07-API契约 §2.5）。
 * 覆盖最近 14 天，供用量趋势 / 成本 / Top 文档 / DAU / 健康度图表演示。
 */
import type {
  DailyUsage,
  DauPoint,
  KnowledgeHealth,
  TokenCost,
  TopDocument,
} from "@/api-client/types";

export const dailyUsage: DailyUsage[] = [
  { date: "2026-07-27", searchCount: 312, qaCount: 86, noAnswerCount: 9, lowConfCount: 14, tokenIn: 420_000, tokenOut: 96_000, activeUsers: 18, cost: 2.41 },
  { date: "2026-07-28", searchCount: 355, qaCount: 102, noAnswerCount: 11, lowConfCount: 13, tokenIn: 512_000, tokenOut: 118_000, activeUsers: 21, cost: 2.96 },
  { date: "2026-07-29", searchCount: 298, qaCount: 78, noAnswerCount: 7, lowConfCount: 10, tokenIn: 388_000, tokenOut: 90_000, activeUsers: 17, cost: 2.24 },
  { date: "2026-07-30", searchCount: 402, qaCount: 121, noAnswerCount: 12, lowConfCount: 18, tokenIn: 596_000, tokenOut: 142_000, activeUsers: 24, cost: 3.45 },
  { date: "2026-07-31", searchCount: 431, qaCount: 138, noAnswerCount: 13, lowConfCount: 20, tokenIn: 681_000, tokenOut: 161_000, activeUsers: 26, cost: 3.92 },
  { date: "2026-08-01", searchCount: 189, qaCount: 54, noAnswerCount: 5, lowConfCount: 8, tokenIn: 268_000, tokenOut: 62_000, activeUsers: 12, cost: 1.55 },
  { date: "2026-08-02", searchCount: 152, qaCount: 41, noAnswerCount: 4, lowConfCount: 6, tokenIn: 201_000, tokenOut: 48_000, activeUsers: 9, cost: 1.18 },
  { date: "2026-08-03", searchCount: 388, qaCount: 114, noAnswerCount: 10, lowConfCount: 15, tokenIn: 561_000, tokenOut: 132_000, activeUsers: 23, cost: 3.24 },
  { date: "2026-08-04", searchCount: 425, qaCount: 129, noAnswerCount: 12, lowConfCount: 17, tokenIn: 637_000, tokenOut: 150_000, activeUsers: 25, cost: 3.67 },
  { date: "2026-08-05", searchCount: 396, qaCount: 107, noAnswerCount: 9, lowConfCount: 14, tokenIn: 529_000, tokenOut: 124_000, activeUsers: 22, cost: 3.05 },
  { date: "2026-08-06", searchCount: 447, qaCount: 141, noAnswerCount: 11, lowConfCount: 19, tokenIn: 702_000, tokenOut: 168_000, activeUsers: 27, cost: 4.05 },
  { date: "2026-08-07", searchCount: 412, qaCount: 123, noAnswerCount: 12, lowConfCount: 16, tokenIn: 611_000, tokenOut: 144_000, activeUsers: 24, cost: 3.52 },
  { date: "2026-08-08", searchCount: 358, qaCount: 96, noAnswerCount: 8, lowConfCount: 12, tokenIn: 474_000, tokenOut: 112_000, activeUsers: 20, cost: 2.73 },
  { date: "2026-08-09", searchCount: 301, qaCount: 82, noAnswerCount: 7, lowConfCount: 11, tokenIn: 402_000, tokenOut: 95_000, activeUsers: 19, cost: 2.31 },
];

export const tokenCosts: TokenCost[] = [
  { modelName: "claude-sonnet-5", tokenIn: 1_820_000, tokenOut: 430_000, cost: 18.6, calls: 1200 },
  { modelName: "bge-m3 (embedding)", tokenIn: 6_400_000, tokenOut: 0, cost: 8.2, calls: 890 },
  { modelName: "claude-haiku-4-5", tokenIn: 980_000, tokenOut: 210_000, cost: 3.1, calls: 1560 },
  { modelName: "paddleocr (OCR)", tokenIn: 0, tokenOut: 0, cost: 5.8, calls: 46 },
];

export const topDocuments: TopDocument[] = [
  { documentId: 5, fileName: "分布式事务模式选型.pdf", kbName: "产品研发知识库", qaCount: 186, searchCount: 524 },
  { documentId: 7, fileName: "Java 编码规范.pdf", kbName: "后端技术规范库", qaCount: 142, searchCount: 488 },
  { documentId: 8, fileName: "SQL与数据库规范.md", kbName: "后端技术规范库", qaCount: 128, searchCount: 431 },
  { documentId: 12, fileName: "常见问题FAQ.pdf", kbName: "客户成功手册", qaCount: 95, searchCount: 367 },
  { documentId: 11, fileName: "工单处理SOP.pdf", kbName: "客户成功手册", qaCount: 71, searchCount: 255 },
];

export const dau: DauPoint[] = [
  { date: "2026-07-27", activeUsers: 18 },
  { date: "2026-07-28", activeUsers: 21 },
  { date: "2026-07-29", activeUsers: 17 },
  { date: "2026-07-30", activeUsers: 24 },
  { date: "2026-07-31", activeUsers: 26 },
  { date: "2026-08-01", activeUsers: 12 },
  { date: "2026-08-02", activeUsers: 9 },
  { date: "2026-08-03", activeUsers: 23 },
  { date: "2026-08-04", activeUsers: 25 },
  { date: "2026-08-05", activeUsers: 22 },
  { date: "2026-08-06", activeUsers: 27 },
  { date: "2026-08-07", activeUsers: 24 },
  { date: "2026-08-08", activeUsers: 20 },
  { date: "2026-08-09", activeUsers: 19 },
];

export const knowledgeHealth: KnowledgeHealth = {
  noAnswerRate: 0.083,
  lowConfRate: 0.127,
  averageConfidence: 0.84,
  freshnessScore: 0.92,
};
