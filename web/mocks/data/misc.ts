/**
 * mock 杂项数据：标签 / 收藏 / 内容源连接器 / 任务 / 通知。
 * （对齐 tag / user_favorite / source_connection / async_task / notification 语义）
 */
import type { Connector, FavoriteItem, NotificationItem, Tag, Task } from "@/api-client/types";

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

/** mock 异步任务：覆盖上传/摄取/索引重建/同步/删除/导出各类型。 */
export const tasks: Task[] = [
  { id: 1, type: "UPLOAD", status: "RUNNING", title: "分布式事务模式选型.pdf", progress: 65, resourceType: "DOCUMENT", resourceId: "5", startedAt: "2026-08-11T01:30:00Z", finishedAt: null, message: "上传中 65%" },
  { id: 2, type: "INGEST", status: "RUNNING", title: "OpenTelemetry 接入指南.md", progress: 40, resourceType: "DOCUMENT", resourceId: "3", startedAt: "2026-08-11T01:20:00Z", finishedAt: null, message: "向量化中" },
  { id: 3, type: "INDEX_BUILD", status: "PENDING", title: "产品研发知识库 索引重建", progress: 0, resourceType: "KB", resourceId: "1", startedAt: "2026-08-11T01:10:00Z", finishedAt: null },
  { id: 4, type: "SYNC", status: "RUNNING", title: "Confluence 后端空间 同步", progress: 78, resourceType: "CONNECTOR", resourceId: "1", startedAt: "2026-08-11T01:00:00Z", finishedAt: null, message: "已处理 78/100 对象" },
  { id: 5, type: "EXPORT", status: "SUCCEEDED", title: "审计日志导出 CSV", progress: 100, resourceType: "AUDIT", resourceId: "req-8f2a1c", startedAt: "2026-08-10T23:00:00Z", finishedAt: "2026-08-10T23:02:00Z" },
  { id: 6, type: "DELETE", status: "FAILED", title: "旧版接口规范.docx 删除", progress: 0, resourceType: "DOCUMENT", resourceId: "6", startedAt: "2026-08-10T22:00:00Z", finishedAt: "2026-08-10T22:01:00Z", message: "权限不足:E-1002" },
];

/** mock 通知：覆盖任务完成/失败/审核待办/配额告警/系统通知。 */
export const notifications: NotificationItem[] = [
  { id: 1, kind: "TASK_DONE", level: "success", title: "审计日志导出完成", body: "导出文件 audit-2026-08.csv 已就绪,有效期 24 小时", read: false, createdAt: "2026-08-10T23:02:00Z", href: "/admin/audit" },
  { id: 2, kind: "TASK_FAILED", level: "error", title: "文档删除失败", body: "旧版接口规范.docx 删除被拒绝(权限不足)", read: false, createdAt: "2026-08-10T22:01:00Z", href: "/documents/6" },
  { id: 3, kind: "REVIEW_TODO", level: "warning", title: "4 篇文档待审核", body: "数据出境合规指引等 4 篇文档等待你的审核", read: false, createdAt: "2026-08-10T01:00:00Z", href: "/governance/review" },
  { id: 4, kind: "QUOTA_WARN", level: "warning", title: "Token 用量达 82%", body: "本月 Token 用量已达配额 82%,预计 3 天后达到上限", read: false, createdAt: "2026-08-09T12:00:00Z", href: "/analytics" },
  { id: 5, kind: "SYSTEM", level: "info", title: "平台维护通知", body: "2026-08-12 02:00-04:00 计划维护,期间问答与上传可能间歇不可用", read: true, createdAt: "2026-08-09T09:00:00Z" },
  { id: 6, kind: "TASK_DONE", level: "success", title: "索引重建完成", body: "后端技术规范库 索引重建完成,共 1,240 分块", read: true, createdAt: "2026-08-08T18:00:00Z", href: "/kbs/2" },
];
