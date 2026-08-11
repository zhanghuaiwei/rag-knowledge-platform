import type { AdminApi } from "@/api-client/contracts/admin";
import type {
  ApiKey,
  AuditLogListParams,
  CreateApiKeyInput,
  CreateWebhookInput,
  Org,
  OrgInput,
  PageParams,
  User,
  Webhook,
} from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";
import { appendAudit, nextId, now } from "@/mocks/helpers";

function notFound(resource: string): never {
  throw new Error(`${resource}不存在`);
}

function findUser(id: number): User {
  const user = db.users.find((item) => item.id === id);
  if (!user) notFound("用户");
  return user;
}

export const adminApi: AdminApi = {
  // ---- 成员与组织 ----
  async listUsers(params: PageParams = {}) {
    await delay();
    return paginate(db.users, params.page, params.size);
  },
  async disableUser(id: number) {
    await delay(250);
    const user = findUser(id);
    user.status = "DISABLED";
    appendAudit({ action: "user.disable", resourceType: "USER", resourceId: id });
    return user;
  },
  async enableUser(id: number) {
    await delay(250);
    const user = findUser(id);
    user.status = "ACTIVE";
    appendAudit({ action: "user.enable", resourceType: "USER", resourceId: id });
    return user;
  },
  async updateUserOrg(userId: number, orgId: number | null) {
    await delay(250);
    const user = findUser(userId);
    const org = orgId === null ? null : db.orgs.find((item) => item.id === orgId);
    user.orgName = org?.name ?? "未分配";
    appendAudit({ action: "user.org.update", resourceType: "USER", resourceId: userId });
    return user;
  },
  async listOrgs() {
    await delay();
    return db.orgs;
  },
  async createOrg(input: OrgInput) {
    await delay(250);
    const parent = input.parentId === null ? null : db.orgs.find((item) => item.id === input.parentId);
    const id = nextId(db.orgs);
    const org: Org = {
      id,
      parentId: input.parentId,
      name: input.name.trim(),
      path: parent ? `${parent.path}${parent.path.endsWith("/") ? "" : "/"}${input.name.trim()}` : `/${input.name.trim()}`,
      memberCount: 0,
      status: "ACTIVE",
    };
    db.orgs.push(org);
    appendAudit({ action: "org.create", resourceType: "ORG", resourceId: id });
    return org;
  },
  async updateOrg(id: number, name: string) {
    await delay(250);
    const org = db.orgs.find((item) => item.id === id);
    if (!org) notFound("组织");
    org.name = name.trim();
    org.path = org.path.replace(/\/[^/]+$/, `/${name.trim()}`);
    appendAudit({ action: "org.update", resourceType: "ORG", resourceId: id });
    return org;
  },
  async deleteOrg(id: number) {
    await delay(250);
    const hasChildren = db.orgs.some((item) => item.parentId === id);
    if (hasChildren) throw new Error("请先清空或迁移该组织下的子部门");
    const index = db.orgs.findIndex((item) => item.id === id);
    if (index < 0) notFound("组织");
    db.orgs.splice(index, 1);
    appendAudit({ action: "org.delete", resourceType: "ORG", resourceId: id });
  },

  // ---- 审计日志 ----
  async listAuditLogs(params: AuditLogListParams = {}) {
    await delay();
    const items = params.result ? db.auditLogs.filter((log) => log.result === params.result) : db.auditLogs;
    return paginate(items, params.page, params.size);
  },

  // ---- API Key ----
  async listApiKeys() {
    await delay();
    return db.apiKeys;
  },
  async createApiKey(input: CreateApiKeyInput) {
    await delay(400);
    const id = nextId(db.apiKeys);
    const key: ApiKey = {
      id,
      name: input.name.trim(),
      keyPrefix: `rk_${Math.random().toString(36).slice(2, 10)}`,
      scopes: input.scopes,
      kbIds: input.kbId ? [input.kbId] : [],
      status: "ACTIVE",
      expiresAt: input.expireDays ? new Date(Date.now() + input.expireDays * 86_400_000).toISOString() : null,
      lastUsedAt: null,
      createdAt: now(),
    };
    db.apiKeys.unshift(key);
    const secret = `rk_${key.keyPrefix.slice(3)}_${Math.random().toString(36).slice(2, 18)}`;
    appendAudit({ action: "apikey.create", resourceType: "API_KEY", resourceId: id });
    return { key, secret };
  },
  async revokeApiKey(id: number) {
    await delay(250);
    const key = db.apiKeys.find((item) => item.id === id);
    if (!key) notFound("API Key");
    key.status = "REVOKED";
    appendAudit({ action: "apikey.revoke", resourceType: "API_KEY", resourceId: id });
  },

  // ---- Webhook ----
  async listWebhooks() {
    await delay();
    return db.webhooks;
  },
  async createWebhook(input: CreateWebhookInput) {
    await delay(300);
    if (input.targetUrl.startsWith("http://") || /^https?:\/\/(127\.0\.0\.1|localhost|10\.|192\.168\.)/.test(input.targetUrl)) {
      throw new Error("目标地址不允许内网或非 HTTPS");
    }
    const hook: Webhook = {
      id: nextId(db.webhooks),
      name: input.name.trim(),
      targetUrl: input.targetUrl.trim(),
      eventTypes: input.eventTypes,
      status: "ACTIVE",
      createdAt: now(),
    };
    db.webhooks.unshift(hook);
    appendAudit({ action: "webhook.create", resourceType: "WEBHOOK", resourceId: hook.id });
    return hook;
  },
  async toggleWebhook(id: number, paused: boolean) {
    await delay(250);
    const hook = db.webhooks.find((item) => item.id === id);
    if (!hook) notFound("Webhook");
    hook.status = paused ? "PAUSED" : "ACTIVE";
    appendAudit({ action: paused ? "webhook.pause" : "webhook.resume", resourceType: "WEBHOOK", resourceId: id });
    return hook;
  },
};
