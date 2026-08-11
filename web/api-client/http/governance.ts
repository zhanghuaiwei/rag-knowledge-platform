/**
 * 治理中心域真实 HTTP transport（对齐 OpenAPI governance + 产品契约新增端点）。
 */
import type { GovernanceApi } from "@/api-client/contracts/governance";
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
import { request, requestVoid } from "@/api-client/http/client";

export const governanceApi: GovernanceApi = {
  // ---- 元数据 schema ----

  async listMetadataSchemas() {
    return request<MetadataSchema[]>({ method: "GET", url: "/metadata-schemas" });
  },

  async createMetadataSchema(input: MetadataSchemaInput) {
    return request<MetadataSchema>({ method: "POST", url: "/metadata-schemas", data: input });
  },

  async publishMetadataSchema(id: number) {
    return request<MetadataSchema>({ method: "POST", url: `/metadata-schemas/${id}/publish` });
  },

  // ---- 保留策略 ----

  async listRetentionPolicies() {
    return request<RetentionPolicy[]>({ method: "GET", url: "/retention-policies" });
  },

  async createRetentionPolicy(input: RetentionPolicyInput) {
    return request<RetentionPolicy>({ method: "POST", url: "/retention-policies", data: input });
  },

  async toggleRetentionPolicy(id: number, enabled: boolean) {
    return request<RetentionPolicy>({
      method: "PATCH",
      url: `/retention-policies/${id}`,
      data: { enabled },
    });
  },

  // ---- 法律保全 ----

  async listLegalHolds() {
    return request<LegalHold[]>({ method: "GET", url: "/legal-holds" });
  },

  async createLegalHold(input: LegalHoldInput) {
    return request<LegalHold>({ method: "POST", url: "/legal-holds", data: input });
  },

  async releaseLegalHold(id: number) {
    return request<LegalHold>({ method: "POST", url: `/legal-holds/${id}/release` });
  },

  // ---- 删除审批与删除证明 ----

  async listDeletionTasks() {
    return request<DeletionTask[]>({ method: "GET", url: "/deletion-tasks" });
  },

  async approveDeletion(id: number) {
    return request<DeletionTask>({ method: "POST", url: `/deletion-tasks/${id}/approve` });
  },

  async listDeletionReceipts() {
    return request<DeletionReceipt[]>({ method: "GET", url: "/deletion-receipts" });
  },

  // ---- 标签 ----

  async listTags() {
    return request<Tag[]>({ method: "GET", url: "/tags" });
  },

  async createTag(name: string) {
    return request<Tag>({ method: "POST", url: "/tags", data: { name } });
  },

  async deleteTag(id: number) {
    await requestVoid({ method: "DELETE", url: `/tags/${id}` });
  },
};
