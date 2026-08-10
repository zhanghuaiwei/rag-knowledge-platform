/**
 * mock transport：从 mocks/db 读取数据，模拟网络延迟后返回。
 *
 * 页面与组件通过 api-client 消费，不感知数据来自 mock 还是真实后端。
 * 数据仅用于本地开发与演示，不代表真实验收证据。
 */
import type { ApiClient } from "@/api-client/client";
import type {
  AuditLogListParams,
  ChatSource,
  DocumentListParams,
  PageParams,
} from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";

function notFound(resource: string): never {
  throw new Error(`${resource}不存在`);
}

export const mockClient: ApiClient = {
  // ---- 认证 ----
  async getCurrentUser() {
    await delay();
    return db.currentUser;
  },

  // ---- 知识库 ----
  async listKbs(params: PageParams = {}) {
    await delay();
    return paginate(db.kbs, params.page, params.size);
  },
  async getKb(id: number) {
    await delay(120);
    const kb = db.kbs.find((item) => item.id === id);
    if (!kb) notFound("知识库");
    return kb;
  },
  async updateKb(id, input) {
    await delay(300);
    const kb = db.kbs.find((item) => item.id === id);
    if (!kb) notFound("知识库");
    if (input.name !== undefined) kb.name = input.name;
    if (input.description !== undefined) kb.description = input.description;
    if (input.requiresReview !== undefined) kb.requiresReview = input.requiresReview;
    kb.updatedAt = new Date().toISOString();
    return kb;
  },
  async listKbMembers(kbId: number) {
    await delay(120);
    const members = db.kbMembers[kbId];
    if (!members) notFound("知识库");
    return members;
  },

  // ---- 文档 ----
  async listDocuments(params: DocumentListParams = {}) {
    await delay();
    const keyword = params.keyword?.trim().toLowerCase();
    let items = db.documents;
    if (params.kbId) items = items.filter((doc) => doc.kbId === params.kbId);
    if (params.ingestStatus) items = items.filter((doc) => doc.ingestStatus === params.ingestStatus);
    if (params.reviewStatus) items = items.filter((doc) => doc.reviewStatus === params.reviewStatus);
    if (params.sensitivity) items = items.filter((doc) => doc.sensitivity === params.sensitivity);
    if (keyword) {
      items = items.filter(
        (doc) =>
          doc.title.toLowerCase().includes(keyword) ||
          doc.fileName.toLowerCase().includes(keyword),
      );
    }
    return paginate(items, params.page, params.size);
  },
  async getDocument(id: number) {
    await delay(120);
    const detail = db.getDocumentDetail(id);
    if (!detail) notFound("文档");
    return detail;
  },
  async listDocumentVersions(documentId: number) {
    await delay(120);
    const detail = db.getDocumentDetail(documentId);
    if (!detail) notFound("文档");
    return detail.versions;
  },
  async uploadDocument(input) {
    await delay(500);
    const kb = db.kbs.find((item) => item.id === input.kbId);
    if (!kb) notFound("知识库");
    const fileExt = input.fileName.split(".").pop()?.toLowerCase() ?? "";
    const doc = {
      id: Math.max(0, ...db.documents.map((item) => item.id)) + 1,
      kbId: kb.id,
      kbName: kb.name,
      title: input.title,
      fileName: input.fileName,
      fileExt,
      mimeType: "application/octet-stream",
      sourceType: "UPLOAD" as const,
      fileSize: input.fileSize,
      versionNo: 1,
      ingestStatus: "PARSING" as const,
      reviewStatus: "DRAFT" as const,
      sensitivity: input.sensitivity,
      ownerName: db.currentUser.name,
      chunkCount: 0,
      updatedAt: new Date().toISOString(),
    };
    db.documents.unshift(doc);
    return doc;
  },

  // ---- 问答 ----
  async listChatSessions(params: PageParams = {}) {
    await delay();
    return paginate(db.chatSessions, params.page, params.size);
  },
  async listChatMessages(sessionId: number) {
    await delay();
    const messages = db.chatMessagesBySession[sessionId];
    if (!messages) notFound("会话");
    return messages;
  },
  async sendChatMessage(input) {
    await delay(400);
    const session = db.chatSessions.find((item) => item.id === input.sessionId);
    if (!session) notFound("会话");

    const sources: ChatSource[] = db.searchItems.slice(0, 2).map((hit) => ({
      documentId: hit.documentId,
      fileName: hit.fileName,
      pageNo: hit.pageNo,
      sectionTitle: hit.sectionTitle,
      chunkId: `mock-chunk-${hit.documentId}`,
      score: hit.score / 20,
    }));

    return {
      sessionId: input.sessionId,
      messageId: 999,
      answerStatus: "ANSWERED",
      confidence: 0.85,
      content:
        `（mock 回答）关于「${input.content}」：接入真实后端后（NEXT_PUBLIC_USE_MOCK=false），` +
        "此处将返回流式 SSE 结果与真实引用。当前数据来自内置 mock 层。",
      sources,
      suggestions: ["基于检索来源的追问 1", "基于检索来源的追问 2"],
      tokenIn: 120,
      tokenOut: 80,
      cost: 0.003,
    };
  },

  // ---- 搜索 ----
  async search(params) {
    await delay(200);
    const keyword = params.keyword.trim().toLowerCase();
    const kbFilter = params.kbIds?.length ? new Set(params.kbIds) : null;
    const items = db.searchItems.filter(
      (item) =>
        item.fileName.toLowerCase().includes(keyword) &&
        (kbFilter ? kbFilter.has(item.kbId) : true),
    );
    return paginate(items, params.page, params.size);
  },

  // ---- 用量与质量 ----
  async getDailyUsage() {
    await delay();
    return db.dailyUsage;
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

  // ---- 管理 ----
  async listUsers(params: PageParams = {}) {
    await delay();
    return paginate(db.users, params.page, params.size);
  },
  async listOrgs() {
    await delay();
    return db.orgs;
  },
  async listAuditLogs(params: AuditLogListParams = {}) {
    await delay();
    const items = params.result ? db.auditLogs.filter((log) => log.result === params.result) : db.auditLogs;
    return paginate(items, params.page, params.size);
  },
  async listApiKeys() {
    await delay();
    return db.apiKeys;
  },
  async listWebhooks() {
    await delay();
    return db.webhooks;
  },
  async listReviews(params: PageParams = {}) {
    await delay();
    return paginate(db.reviewItems, params.page, params.size);
  },

  // ---- 杂项 ----
  async listTags() {
    await delay();
    return db.tags;
  },
  async listFavorites(params: PageParams = {}) {
    await delay();
    return paginate(db.favorites, params.page, params.size);
  },
  async listConnectors() {
    await delay();
    return db.connectors;
  },
};
