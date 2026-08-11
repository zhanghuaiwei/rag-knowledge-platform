/** 文档域类型与权限（ACL）类型。 */
import type { PageParams } from "@/api-client/types/common";

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
  /** 摄取失败重试次数（解析失败重试上限 3 次，F2.2-4.2.5）。 */
  retryCount?: number;
}

export interface DocumentListParams extends PageParams {
  kbId?: number;
  ingestStatus?: IngestStatus;
  reviewStatus?: ReviewStatus;
  sensitivity?: Sensitivity;
  keyword?: string;
  tagId?: number;
}

/** 文档上传请求：真实实现为分片上传 + 安全扫描（GKB-03），此处为最小字段集。 */
export interface UploadDocumentInput {
  kbId: number;
  title: string;
  fileName: string;
  fileSize: number;
  sensitivity: Sensitivity;
}

/** 文档元数据可编辑字段（租户 schema 驱动，最小子集）。 */
export interface UpdateDocumentMetadataInput {
  title?: string;
  sensitivity?: Sensitivity;
  tags?: string[];
  ownerName?: string;
}

// ---- 文档级权限（F2.14 / GKB-04） ----

/** 权限点：三档分离（view_excerpt / view_content / download_original）。 */
export type PermissionPoint = "VIEW_EXCERPT" | "VIEW_CONTENT" | "DOWNLOAD_ORIGINAL";

/** ACL 授权主体类型：用户 / 部门（含子部门） / 系统角色 / 知识库角色。 */
export type AclPrincipalType = "USER" | "ORG" | "ROLE" | "KB_ROLE";

export interface AclEntry {
  id: number;
  principalType: AclPrincipalType;
  principalName: string;
  permissions: PermissionPoint[];
}

export interface AclSetRequest {
  /** 覆盖式写入：调用方传入完整目标列表（白名单语义）。 */
  entries: Omit<AclEntry, "id">[];
}
