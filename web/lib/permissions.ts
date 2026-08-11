/**
 * 前端权限码常量（与服务端 PermissionCatalog 对齐，OpenAPI 权限契约评审前视为草案）。
 *
 * 菜单/路由守卫/按钮只用这些稳定能力码判断；不重复定义角色枚举或硬编码字符串。
 */
export const PERMISSION = {
  DASHBOARD_VIEW: "dashboard:view",
  CHAT_USE: "chat:use",
  SEARCH_EXECUTE: "search:execute",
  KB_LIST: "kb:list",
  KB_MANAGE: "kb:manage",
  DOCUMENT_LIST: "document:list",
  FAVORITE_LIST: "favorite:list",
  REVIEW_LIST: "review:list",
  REVIEW_DECIDE: "review:decide",
  METADATA_SCHEMA_MANAGE: "metadata-schema:manage",
  RETENTION_MANAGE: "retention:manage",
  DELETION_READ: "deletion:read",
  ANALYTICS_READ: "analytics:read",
  ANALYTICS_SCREEN: "analytics:screen",
  TENANT_MEMBER_MANAGE: "tenant-member:manage",
  TAG_MANAGE: "tag:manage",
  API_KEY_MANAGE: "api-key:manage",
  WEBHOOK_MANAGE: "webhook:manage",
  AUDIT_READ: "audit:read",
} as const;

export type Permission = (typeof PERMISSION)[keyof typeof PERMISSION];

/** 管理中心任意入口所需权限（成员/标签/API Key/Webhook/审计）。 */
export const ADMIN_PERMISSIONS: readonly Permission[] = [
  PERMISSION.TENANT_MEMBER_MANAGE,
  PERMISSION.TAG_MANAGE,
  PERMISSION.API_KEY_MANAGE,
  PERMISSION.WEBHOOK_MANAGE,
  PERMISSION.AUDIT_READ,
];

/** 治理中心任意入口所需权限（审核/元数据/保留/删除）。 */
export const GOVERNANCE_PERMISSIONS: readonly Permission[] = [
  PERMISSION.REVIEW_LIST,
  PERMISSION.METADATA_SCHEMA_MANAGE,
  PERMISSION.RETENTION_MANAGE,
  PERMISSION.DELETION_READ,
];
