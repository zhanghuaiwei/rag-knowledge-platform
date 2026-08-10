/**
 * 统一 API 客户端：按环境变量自动选择 transport。
 *
 * - 默认（NEXT_PUBLIC_USE_MOCK=true 或未设置）：使用内置 mock 数据，脱离后端可演示。
 * - NEXT_PUBLIC_USE_MOCK=false：使用真实 HTTP transport（OpenAPI v0.2 冻结后接入）。
 *
 * 页面只依赖本模块的 `api`，禁止组件直接 fetch 或拼接完整请求地址。
 */
import { httpClient } from "@/api-client/http";
import { mockClient } from "@/api-client/mock";
import type {
  ApiKey,
  AuditLog,
  AuditLogListParams,
  ChatMessage,
  ChatMessageInput,
  ChatSession,
  ChatStreamResult,
  Connector,
  CurrentUser,
  DailyUsage,
  DauPoint,
  DocumentDetail,
  DocumentListParams,
  DocumentSummary,
  DocumentVersion,
  FavoriteItem,
  Kb,
  KbMember,
  KnowledgeHealth,
  Org,
  PageParams,
  PageResult,
  ReviewItem,
  SearchItem,
  SearchParams,
  Tag,
  TokenCost,
  TopDocument,
  UpdateKbInput,
  UploadDocumentInput,
  User,
  Webhook,
} from "@/api-client/types";

/** API 客户端接口：所有域的统一契约（与 OpenAPI 对齐）。 */
export interface ApiClient {
  // ---- 认证 ----
  getCurrentUser(): Promise<CurrentUser>;

  // ---- 知识库 ----
  listKbs(params?: PageParams): Promise<PageResult<Kb>>;
  getKb(id: number): Promise<Kb>;
  updateKb(id: number, input: UpdateKbInput): Promise<Kb>;
  listKbMembers(kbId: number): Promise<KbMember[]>;

  // ---- 文档 ----
  listDocuments(params?: DocumentListParams): Promise<PageResult<DocumentSummary>>;
  getDocument(id: number): Promise<DocumentDetail>;
  listDocumentVersions(documentId: number): Promise<DocumentVersion[]>;
  uploadDocument(input: UploadDocumentInput): Promise<DocumentSummary>;

  // ---- 问答 ----
  listChatSessions(params?: PageParams): Promise<PageResult<ChatSession>>;
  listChatMessages(sessionId: number): Promise<ChatMessage[]>;
  sendChatMessage(input: ChatMessageInput): Promise<ChatStreamResult>;

  // ---- 搜索 ----
  search(params: SearchParams): Promise<PageResult<SearchItem>>;

  // ---- 用量与质量 ----
  getDailyUsage(): Promise<DailyUsage[]>;
  getTokenCosts(): Promise<TokenCost[]>;
  getTopDocuments(): Promise<TopDocument[]>;
  getDau(): Promise<DauPoint[]>;
  getKnowledgeHealth(): Promise<KnowledgeHealth>;

  // ---- 管理 ----
  listUsers(params?: PageParams): Promise<PageResult<User>>;
  listOrgs(): Promise<Org[]>;
  listAuditLogs(params?: AuditLogListParams): Promise<PageResult<AuditLog>>;
  listApiKeys(): Promise<ApiKey[]>;
  listWebhooks(): Promise<Webhook[]>;
  listReviews(params?: PageParams): Promise<PageResult<ReviewItem>>;

  // ---- 杂项 ----
  listTags(): Promise<Tag[]>;
  listFavorites(params?: PageParams): Promise<PageResult<FavoriteItem>>;
  listConnectors(): Promise<Connector[]>;
}

const useMock = process.env.NEXT_PUBLIC_USE_MOCK !== "false";

/** 唯一客户端实例。 */
export const api: ApiClient = useMock ? mockClient : httpClient;
