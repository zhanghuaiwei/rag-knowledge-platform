import type {
  DeletionReceipt,
  DeletionTask,
  LegalHold,
  LegalHoldInput,
  MetadataSchema,
  MetadataSchemaInput,
  RetentionPolicy,
  RetentionPolicyInput,
  Tag,
} from "@/api-client/types";

/** 治理中心契约（GKB-04）：元数据 schema / 保留与法律保全 / 删除与证明 / 标签。 */
export interface GovernanceApi {
  listMetadataSchemas(): Promise<MetadataSchema[]>;
  createMetadataSchema(input: MetadataSchemaInput): Promise<MetadataSchema>;
  publishMetadataSchema(id: number): Promise<MetadataSchema>;
  listRetentionPolicies(): Promise<RetentionPolicy[]>;
  createRetentionPolicy(input: RetentionPolicyInput): Promise<RetentionPolicy>;
  toggleRetentionPolicy(id: number, enabled: boolean): Promise<RetentionPolicy>;
  listLegalHolds(): Promise<LegalHold[]>;
  createLegalHold(input: LegalHoldInput): Promise<LegalHold>;
  releaseLegalHold(id: number): Promise<LegalHold>;
  listDeletionTasks(): Promise<DeletionTask[]>;
  approveDeletion(id: number): Promise<DeletionTask>;
  listDeletionReceipts(): Promise<DeletionReceipt[]>;
  listTags(): Promise<Tag[]>;
  createTag(name: string): Promise<Tag>;
  deleteTag(id: number): Promise<void>;
}
