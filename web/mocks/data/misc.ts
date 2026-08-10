/**
 * mock 杂项数据：标签 / 收藏 / 内容源连接器。
 * （对齐 tag / user_favorite / source_connection 语义）
 */
import type { Connector, FavoriteItem, Tag } from "@/api-client/types";

export const tags: Tag[] = [
  { id: 1, name: "架构设计", documentCount: 6 },
  { id: 2, name: "规范", documentCount: 9 },
  { id: 3, name: "合规", documentCount: 3 },
  { id: 4, name: "SOP", documentCount: 4 },
  { id: 5, name: "待评审", documentCount: 2 },
];

export const favorites: FavoriteItem[] = [
  { documentId: 5, title: "分布式事务模式选型", fileName: "分布式事务模式选型.pdf", kbName: "产品研发知识库", savedAt: "2026-08-08T09:00:00Z" },
  { documentId: 8, title: "SQL 与数据库规范", fileName: "SQL与数据库规范.md", kbName: "后端技术规范库", savedAt: "2026-08-06T02:00:00Z" },
  { documentId: 11, title: "工单处理 SOP", fileName: "工单处理SOP.pdf", kbName: "客户成功手册", savedAt: "2026-08-03T05:00:00Z" },
];

export const connectors: Connector[] = [
  { id: 1, name: "Confluence 后端空间", providerKey: "confluence", syncMode: "SCHEDULED", status: "ACTIVE", lastSuccessAt: "2026-08-10T01:00:00Z", lastErrorCode: null, cursorAgeMin: 5, counts: { discovered: 22, created: 3, updated: 12, deleted: 1, failed: 0 } },
  { id: 2, name: "SharePoint 文档库", providerKey: "sharepoint", syncMode: "WEBHOOK", status: "ERROR", lastSuccessAt: "2026-08-08T03:00:00Z", lastErrorCode: "E_OAUTH_EXPIRED", cursorAgeMin: 3120, counts: { discovered: 40, created: 2, updated: 5, deleted: 0, failed: 2 } },
  { id: 3, name: "团队 Wiki", providerKey: "web", syncMode: "MANUAL", status: "PAUSED", lastSuccessAt: "2026-07-30T06:00:00Z", lastErrorCode: null, cursorAgeMin: 15840, counts: { discovered: 15, created: 0, updated: 0, deleted: 0, failed: 0 } },
];
