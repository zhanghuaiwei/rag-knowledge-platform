/**
 * mock 内存库通用工具：ID 生成、时间戳、审计追加。
 * 仅供本地开发与演示；真实审计由后端追加写实现（GKB-04）。
 */
import type { AuditLog } from "@/api-client/types";
import { auditLogs } from "@/mocks/data/admin";

export function now(): string {
  return new Date().toISOString();
}

/** 从现有集合推导下一个自增 ID。 */
export function nextId(items: { id: number }[]): number {
  return Math.max(0, ...items.map((item) => item.id)) + 1;
}

interface AuditDraft {
  action: string;
  resourceType: string;
  resourceId: string | number;
  actor?: string;
  actorType?: AuditLog["actorType"];
  result?: AuditLog["result"];
  reasonCode?: string | null;
}

/** 追加一条审计日志（写入内存库头部，模拟 append-only）。 */
export function appendAudit(draft: AuditDraft): AuditLog {
  const log: AuditLog = {
    id: nextId(auditLogs),
    actor: draft.actor ?? "系统",
    actorType: draft.actorType ?? "USER",
    action: draft.action,
    resourceType: draft.resourceType,
    resourceId: String(draft.resourceId),
    result: draft.result ?? "SUCCEEDED",
    reasonCode: draft.reasonCode ?? null,
    requestId: `req-${Math.random().toString(36).slice(2, 10)}`,
    occurredAt: now(),
  };
  auditLogs.unshift(log);
  return log;
}
