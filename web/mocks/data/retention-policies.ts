/**
 * mock 保留策略与法律保全（GKB-04）。
 * 保留策略按租户/知识库/分类配置；法律保全对象阻断清理（不可删除/过期）。
 */
import type { LegalHold, RetentionPolicy } from "@/api-client/types";

export const retentionPolicies: RetentionPolicy[] = [
  { id: 1, name: "默认保留 3 年", appliesTo: "TENANT", targetId: null, durationMonths: 36, action: "REVIEW", enabled: true, createdAt: "2026-06-01T00:00:00Z" },
  { id: 2, name: "合规库长期保留", appliesTo: "KB", targetId: 5, durationMonths: 120, action: "RETAIN", enabled: true, createdAt: "2026-06-15T00:00:00Z" },
  { id: 3, name: "临时文档自动过期", appliesTo: "CATEGORY", targetId: 3, durationMonths: 12, action: "AUTO_EXPIRE", enabled: false, createdAt: "2026-07-01T00:00:00Z" },
];

export const legalHolds: LegalHold[] = [
  {
    id: 1,
    name: "合规调查保全",
    reason: "法务要求保全数据出境相关文档，阻断清理与过期。",
    documentIds: [14, 15],
    createdBy: "孙志强",
    createdAt: "2026-07-20T01:00:00Z",
    releasedAt: null,
  },
];
