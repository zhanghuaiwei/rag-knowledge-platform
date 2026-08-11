import type { GovernanceApi } from "@/api-client/contracts/governance";
import type {
  LegalHold,
  LegalHoldInput,
  MetadataSchema,
  MetadataSchemaInput,
  RetentionPolicy,
  RetentionPolicyInput,
  Tag,
} from "@/api-client/types";
import { db, delay } from "@/mocks/db";
import { appendAudit, nextId, now } from "@/mocks/helpers";

function notFound(resource: string): never {
  throw new Error(`${resource}不存在`);
}

export const governanceApi: GovernanceApi = {
  // ---- 元数据 schema ----
  async listMetadataSchemas() {
    await delay();
    return db.metadataSchemas;
  },
  async createMetadataSchema(input: MetadataSchemaInput) {
    await delay(300);
    if (db.metadataSchemas.some((item) => item.name === input.name.trim())) {
      throw new Error("schema 名称已存在");
    }
    const schema: MetadataSchema = {
      id: nextId(db.metadataSchemas),
      name: input.name.trim(),
      description: input.description?.trim() ?? "",
      fields: input.fields.map((f) => ({ ...f, options: f.options ? [...f.options] : undefined })),
      status: "DRAFT",
      updatedAt: now(),
    };
    db.metadataSchemas.push(schema);
    appendAudit({ action: "metadata.schema.create", resourceType: "SCHEMA", resourceId: schema.id });
    return schema;
  },
  async publishMetadataSchema(id: number) {
    await delay(250);
    const schema = db.metadataSchemas.find((item) => item.id === id);
    if (!schema) notFound("schema");
    schema.status = "PUBLISHED";
    schema.updatedAt = now();
    appendAudit({ action: "metadata.schema.publish", resourceType: "SCHEMA", resourceId: id });
    return schema;
  },

  // ---- 保留策略 ----
  async listRetentionPolicies() {
    await delay();
    return db.retentionPolicies;
  },
  async createRetentionPolicy(input: RetentionPolicyInput) {
    await delay(300);
    const policy: RetentionPolicy = {
      id: nextId(db.retentionPolicies),
      name: input.name.trim(),
      appliesTo: input.appliesTo,
      targetId: null,
      durationMonths: input.durationMonths,
      action: input.action,
      enabled: true,
      createdAt: now(),
    };
    db.retentionPolicies.push(policy);
    appendAudit({ action: "retention.policy.create", resourceType: "RETENTION", resourceId: policy.id });
    return policy;
  },
  async toggleRetentionPolicy(id: number, enabled: boolean) {
    await delay(200);
    const policy = db.retentionPolicies.find((item) => item.id === id);
    if (!policy) notFound("保留策略");
    policy.enabled = enabled;
    return policy;
  },

  // ---- 法律保全 ----
  async listLegalHolds() {
    await delay();
    return db.legalHolds;
  },
  async createLegalHold(input: LegalHoldInput) {
    await delay(300);
    const hold: LegalHold = {
      id: nextId(db.legalHolds),
      name: input.name.trim(),
      reason: input.reason.trim(),
      documentIds: [...input.documentIds],
      createdBy: db.currentUser.name,
      createdAt: now(),
      releasedAt: null,
    };
    db.legalHolds.unshift(hold);
    appendAudit({ action: "legal_hold.create", resourceType: "LEGAL_HOLD", resourceId: hold.id });
    return hold;
  },
  async releaseLegalHold(id: number) {
    await delay(250);
    const hold = db.legalHolds.find((item) => item.id === id);
    if (!hold) notFound("法律保全");
    hold.releasedAt = now();
    appendAudit({ action: "legal_hold.release", resourceType: "LEGAL_HOLD", resourceId: id });
    return hold;
  },

  // ---- 删除任务与证明 ----
  async listDeletionTasks() {
    await delay();
    return db.deletionTasks;
  },
  async approveDeletion(id: number) {
    await delay(250);
    const task = db.deletionTasks.find((item) => item.id === id);
    if (!task) notFound("删除任务");
    task.status = "RUNNING";
    task.progress = { storage: true, index: true, cache: true, backup: false };
    appendAudit({ action: "deletion.approve", resourceType: "DELETION", resourceId: id });
    return task;
  },
  async listDeletionReceipts() {
    await delay();
    return db.deletionReceipts;
  },

  // ---- 标签 ----
  async listTags() {
    await delay();
    return db.tags;
  },
  async createTag(name: string) {
    await delay(250);
    if (db.tags.some((item) => item.name === name.trim())) throw new Error("标签已存在");
    const tag: Tag = { id: nextId(db.tags), name: name.trim(), documentCount: 0 };
    db.tags.push(tag);
    appendAudit({ action: "tag.create", resourceType: "TAG", resourceId: tag.id });
    return tag;
  },
  async deleteTag(id: number) {
    await delay(250);
    const index = db.tags.findIndex((item) => item.id === id);
    if (index < 0) notFound("标签");
    db.tags.splice(index, 1);
    appendAudit({ action: "tag.delete", resourceType: "TAG", resourceId: id });
  },
};
