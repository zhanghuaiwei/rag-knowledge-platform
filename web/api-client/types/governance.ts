/** 治理中心域类型：审核 / 元数据 schema / 保留与法律保全 / 删除与证明 / 标签（F2.13 / GKB-04）。 */
import type { Sensitivity } from "@/api-client/types/document";

// ---- 内容审核（F2.13） ----

export interface ReviewItem {
  documentId: number;
  title: string;
  kbName: string;
  submitter: string;
  sensitivity: Sensitivity;
  submittedAt: string;
  commentCount: number;
}

export interface ReviewActionInput {
  ids: number[];
  comment?: string;
}

// ---- 元数据 schema（GKB-04） ----

export type MetadataFieldType = "STRING" | "ENUM" | "DATE" | "MULTI_VALUE" | "REFERENCE";

export interface MetadataField {
  key: string;
  label: string;
  type: MetadataFieldType;
  required: boolean;
  /** ENUM 类型的受控词表。 */
  options?: string[];
}

export interface MetadataSchema {
  id: number;
  name: string;
  description: string;
  fields: MetadataField[];
  status: "DRAFT" | "PUBLISHED";
  updatedAt: string;
}

export interface MetadataSchemaInput {
  name: string;
  description?: string;
  fields: MetadataField[];
}

// ---- 保留策略与法律保全 ----

export interface RetentionPolicy {
  id: number;
  name: string;
  appliesTo: "TENANT" | "KB" | "CATEGORY";
  /** 范围目标：TENANT 时为 null，KB/CATEGORY 时为目标 id。 */
  targetId: number | null;
  durationMonths: number;
  action: "AUTO_EXPIRE" | "REVIEW" | "RETAIN";
  enabled: boolean;
  createdAt: string;
}

export interface RetentionPolicyInput {
  name: string;
  appliesTo: RetentionPolicy["appliesTo"];
  durationMonths: number;
  action: RetentionPolicy["action"];
}

export interface LegalHold {
  id: number;
  name: string;
  documentIds: number[];
  reason: string;
  createdBy: string;
  createdAt: string;
  releasedAt: string | null;
}

export interface LegalHoldInput {
  name: string;
  reason: string;
  documentIds: number[];
}

// ---- 删除审批与删除证明 ----

export type DeletionTaskStatus = "PENDING_APPROVAL" | "RUNNING" | "SUCCEEDED" | "FAILED";

export interface DeletionTask {
  id: number;
  documentId: number;
  fileName: string;
  reason: string;
  requestedBy: string;
  status: DeletionTaskStatus;
  createdAt: string;
  /** 各副本处置进度（对象存储/索引/缓存/备份）。 */
  progress: {
    storage: boolean;
    index: boolean;
    cache: boolean;
    backup: boolean;
  };
}

export interface DeletionReceipt {
  id: number;
  taskId: number;
  documentId: number;
  fileName: string;
  checksum: string;
  deletedAt: string;
  operator: string;
}

// ---- 标签 ----

export interface Tag {
  id: number;
  name: string;
  documentCount: number;
}
