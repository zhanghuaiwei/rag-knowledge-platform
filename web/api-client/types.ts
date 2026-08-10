/**
 * API 契约类型定义（对齐 docs/api/server.openapi.yaml 与 docs/07-API契约.md）。
 * OpenAPI v0.2 冻结后改为由契约生成 client，不再手工维护字段。
 * 枚举值以服务端契约为准，前端不重复定义"魔法值"。
 */

// ---- 通用 -------------------------------------------------------------

export interface PageParams {
  page?: number;
  size?: number;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  hasMore: boolean;
}

// ---- 认证 -------------------------------------------------------------

export interface CurrentUser {
  id: number;
  name: string;
  email: string;
  tenantId: number;
  tenantName: string;
  roles: string[];
  orgName: string;
}

// ---- 知识库 -----------------------------------------------------------

export type KbVisibility = "PRIVATE" | "TENANT";
export type KbStatus = "ACTIVE" | "ARCHIVED" | "DELETING";
export type KbMemberRole = "OWNER" | "EDITOR" | "VIEWER";

export interface KbMember {
  userId: number;
  userName: string;
  role: KbMemberRole;
}

export interface Kb {
  id: number;
  name: string;
  description: string;
  visibility: KbVisibility;
  status: KbStatus;
  role: KbMemberRole; // 当前用户在该 KB 的角色
  documentCount: number;
  chunkCount: number;
  dataRegion: string;
  indexProfileName: string;
  requiresReview: boolean;
  ocrEnabled: boolean;
  createdAt: string;
  updatedAt: string;
  members: KbMember[];
}

// ---- 文档 -------------------------------------------------------------

export type IngestStatus =
  | "UPLOADING"
  | "QUARANTINED"
  | "SCANNING"
  | "PARSING"
  | "CHUNKING"
  | "EMBEDDING"
  | "INDEXING"
  | "READY"
  | "FAILED"
  | "BLOCKED";

export type ReviewStatus =
  | "DRAFT"
  | "PENDING_REVIEW"
  | "PUBLISHED"
  | "REJECTED"
  | "WITHDRAWN";

export type Sensitivity = "PUBLIC" | "INTERNAL" | "CONFIDENTIAL" | "RESTRICTED";

export interface DocumentSummary {
  id: number;
  kbId: number;
  kbName: string;
  title: string;
  fileName: string;
  fileExt: string;
  mimeType: string;
  sourceType: "UPLOAD" | "CONNECTOR" | "WEB";
  fileSize: number;
  versionNo: number;
  ingestStatus: IngestStatus;
  reviewStatus: ReviewStatus;
  sensitivity: Sensitivity;
  ownerName: string;
  chunkCount: number;
  updatedAt: string;
}

export interface DocumentVersion {
  versionNo: number;
  fileSize: number;
  ingestStatus: IngestStatus;
  safetyStatus: "PENDING" | "PASSED" | "BLOCKED" | "FAILED";
  chunkCount: number;
  createdBy: string;
  createdAt: string;
}

export interface DocumentDetail extends DocumentSummary {
  versions: DocumentVersion[];
  tags: string[];
  isFavorite: boolean;
}

export interface DocumentListParams extends PageParams {
  kbId?: number;
  ingestStatus?: IngestStatus;
  reviewStatus?: ReviewStatus;
  sensitivity?: Sensitivity;
  keyword?: string;
}

// ---- 问答 -------------------------------------------------------------

export interface ChatSession {
  id: number;
  title: string;
  status: "ACTIVE" | "ARCHIVED";
  kbIds: number[];
  messageCount: number;
  createdAt: string;
  updatedAt: string;
}

export type AnswerStatus =
  | "ANSWERED"
  | "NO_ANSWER"
  | "LOW_CONFIDENCE"
  | "BLOCKED";

export interface ChatSource {
  documentId: number;
  fileName: string;
  pageNo: number;
  sectionTitle: string;
  chunkId: string;
  score: number;
}

export interface ChatMessage {
  id: number;
  sessionId: number;
  seq: number;
  role: "USER" | "ASSISTANT";
  content: string;
  answerStatus: AnswerStatus | null;
  confidence: number | null;
  feedback: -1 | 0 | 1;
  tokenIn: number;
  tokenOut: number;
  modelName: string;
  sources: ChatSource[];
  suggestions: string[];
  createdAt: string;
}

export interface ChatMessageInput {
  sessionId: number;
  content: string;
  kbIds: number[];
}

export interface ChatStreamResult {
  sessionId: number;
  messageId: number;
  answerStatus: AnswerStatus;
  confidence: number;
  content: string;
  sources: ChatSource[];
  suggestions: string[];
  tokenIn: number;
  tokenOut: number;
  cost: number;
}

// ---- 搜索 -------------------------------------------------------------

export interface SearchParams extends PageParams {
  keyword: string;
  kbIds?: number[];
  dateFrom?: string;
  dateTo?: string;
}

export interface SearchItem {
  documentId: number;
  fileName: string;
  kbId: number;
  pageNo: number;
  sectionTitle: string;
  fileExt: string;
  snippet: string;
  score: number;
  updatedAt: string;
}

// ---- 用量与质量 -------------------------------------------------------

export interface DailyUsage {
  date: string;
  searchCount: number;
  qaCount: number;
  noAnswerCount: number;
  lowConfCount: number;
  tokenIn: number;
  tokenOut: number;
  activeUsers: number;
  cost: number;
}

export interface TokenCost {
  modelName: string;
  tokenIn: number;
  tokenOut: number;
  cost: number;
  calls: number;
}

export interface TopDocument {
  documentId: number;
  fileName: string;
  kbName: string;
  qaCount: number;
  searchCount: number;
}

export interface DauPoint {
  date: string;
  activeUsers: number;
}

export interface KnowledgeHealth {
  noAnswerRate: number;
  lowConfRate: number;
  averageConfidence: number;
  freshnessScore: number;
}

// ---- 管理 -------------------------------------------------------------

export interface User {
  id: number;
  name: string;
  email: string;
  status: "ACTIVE" | "DISABLED";
  role: string;
  orgName: string;
  lastLoginAt: string;
}

export interface Org {
  id: number;
  parentId: number | null;
  name: string;
  path: string;
  memberCount: number;
  status: "ACTIVE" | "DISABLED";
}

export interface AuditLog {
  id: number;
  actor: string;
  actorType: "USER" | "API_KEY" | "SERVICE" | "SYSTEM";
  action: string;
  resourceType: string;
  resourceId: string;
  result: "SUCCEEDED" | "DENIED" | "FAILED";
  reasonCode: string | null;
  requestId: string;
  occurredAt: string;
}

export interface ApiKey {
  id: number;
  name: string;
  keyPrefix: string;
  scopes: string[];
  kbIds: number[];
  status: "ACTIVE" | "REVOKED" | "EXPIRED";
  expiresAt: string | null;
  lastUsedAt: string | null;
  createdAt: string;
}

export interface Webhook {
  id: number;
  name: string;
  targetUrl: string;
  eventTypes: string[];
  status: "ACTIVE" | "PAUSED";
  createdAt: string;
}

export interface ReviewItem {
  documentId: number;
  title: string;
  kbName: string;
  submitter: string;
  sensitivity: Sensitivity;
  submittedAt: string;
  commentCount: number;
}

// ---- 杂项 -------------------------------------------------------------

export interface Tag {
  id: number;
  name: string;
  documentCount: number;
}

export interface FavoriteItem {
  documentId: number;
  title: string;
  fileName: string;
  kbName: string;
  savedAt: string;
}

export interface Connector {
  id: number;
  name: string;
  providerKey: string;
  syncMode: "MANUAL" | "SCHEDULED" | "WEBHOOK";
  status: "ACTIVE" | "PAUSED" | "ERROR";
  lastSuccessAt: string | null;
  lastErrorCode: string | null;
  cursorAgeMin: number;
  counts: {
    discovered: number;
    created: number;
    updated: number;
    deleted: number;
    failed: number;
  };
}
