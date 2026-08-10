/**
 * mock 管理后台数据：审计日志 / API Key / Webhook / 审核队列。
 * （对齐 audit_log / api_key / webhook_subscription / document_review 语义）
 */
import type { ApiKey, AuditLog, ReviewItem, Webhook } from "@/api-client/types";

export const auditLogs: AuditLog[] = [
  { id: 9001, actor: "张怀伟", actorType: "USER", action: "kb.create", resourceType: "KB", resourceId: "5", result: "SUCCEEDED", reasonCode: null, requestId: "req-8f2a1c", occurredAt: "2026-08-10T01:10:00Z" },
  { id: 9002, actor: "李佳宁", actorType: "USER", action: "document.upload", resourceType: "DOCUMENT", resourceId: "3", result: "SUCCEEDED", reasonCode: null, requestId: "req-7b31dd", occurredAt: "2026-08-10T00:30:00Z" },
  { id: 9003, actor: "system", actorType: "SYSTEM", action: "document.version.publish", resourceType: "DOCUMENT", resourceId: "1", result: "SUCCEEDED", reasonCode: null, requestId: "req-6c40aa", occurredAt: "2026-08-08T06:10:00Z" },
  { id: 9004, actor: "api-key:kb-search-1", actorType: "API_KEY", action: "chat.message.send", resourceType: "CHAT", resourceId: "101", result: "SUCCEEDED", reasonCode: null, requestId: "req-5d9e77", occurredAt: "2026-08-10T01:05:00Z" },
  { id: 9005, actor: "王建国", actorType: "USER", action: "document.delete", resourceType: "DOCUMENT", resourceId: "6", result: "DENIED", reasonCode: "E-1002", requestId: "req-4c8b33", occurredAt: "2026-08-10T02:05:00Z" },
  { id: 9006, actor: "刘思彤", actorType: "USER", action: "document.acl.update", resourceType: "DOCUMENT", resourceId: "13", result: "SUCCEEDED", reasonCode: null, requestId: "req-3b7f12", occurredAt: "2026-08-07T09:30:00Z" },
  { id: 9007, actor: "孙志强", actorType: "USER", action: "review.approve", resourceType: "DOCUMENT", resourceId: "14", result: "FAILED", reasonCode: "E-2201", requestId: "req-2a6e55", occurredAt: "2026-08-07T02:45:00Z" },
  { id: 9008, actor: "system", actorType: "SYSTEM", action: "index.build.publish", resourceType: "INDEX", resourceId: "kb2-b3", result: "SUCCEEDED", reasonCode: null, requestId: "req-19d9ab", occurredAt: "2026-08-06T05:00:00Z" },
];

export const apiKeys: ApiKey[] = [
  { id: 1, name: "kb-search-1", keyPrefix: "rk_live_8f2a", scopes: ["chat:read", "search:read"], kbIds: [1, 2], status: "ACTIVE", expiresAt: "2027-08-10T00:00:00Z", lastUsedAt: "2026-08-10T01:05:00Z", createdAt: "2026-07-15T02:00:00Z" },
  { id: 2, name: "ops-exporter", keyPrefix: "rk_live_3c91", scopes: ["analytics:read"], kbIds: [], status: "ACTIVE", expiresAt: "2027-01-01T00:00:00Z", lastUsedAt: "2026-08-09T23:50:00Z", createdAt: "2026-07-01T01:00:00Z" },
  { id: 3, name: "legacy-client", keyPrefix: "rk_live_aa11", scopes: ["chat:read", "search:read"], kbIds: [1], status: "REVOKED", expiresAt: null, lastUsedAt: "2026-06-20T10:00:00Z", createdAt: "2026-06-01T00:00:00Z" },
];

export const webhooks: Webhook[] = [
  { id: 1, name: "合规事件推送", targetUrl: "https://hooks.example.com/compliance", eventTypes: ["document.review.updated", "legal_hold.changed"], status: "ACTIVE", createdAt: "2026-07-10T06:00:00Z" },
  { id: 2, name: "索引构建通知", targetUrl: "https://hooks.example.com/index-ops", eventTypes: ["index.build.published", "index.build.failed"], status: "PAUSED", createdAt: "2026-07-20T03:00:00Z" },
];

export const reviewItems: ReviewItem[] = [
  { documentId: 14, title: "数据出境合规指引", kbName: "合规与法务库", submitter: "孙志强", sensitivity: "RESTRICTED", submittedAt: "2026-08-07T02:40:00Z", commentCount: 2 },
  { documentId: 9, title: "接口幂等设计规范", kbName: "后端技术规范库", submitter: "王建国", sensitivity: "INTERNAL", submittedAt: "2026-08-04T05:00:00Z", commentCount: 0 },
  { documentId: 3, title: "OpenTelemetry 接入指南", kbName: "产品研发知识库", submitter: "张怀伟", sensitivity: "INTERNAL", submittedAt: "2026-08-10T00:30:00Z", commentCount: 1 },
  { documentId: 16, title: "Confluence 文档自动同步", kbName: "后端技术规范库", submitter: "李佳宁", sensitivity: "INTERNAL", submittedAt: "2026-08-10T01:00:00Z", commentCount: 0 },
];
