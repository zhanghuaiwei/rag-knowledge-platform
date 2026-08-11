/**
 * 文档域真实 HTTP transport（对齐 OpenAPI documents / upload / tags / favorites）。
 * 上传与重试为异步任务：轮询终态后回读文档。
 */
import type { DocumentApi } from "@/api-client/contracts/document";
import type {
  AclEntry,
  AclSetRequest,
  DocumentDetail,
  DocumentListParams,
  DocumentSummary,
  DocumentVersion,
  PageResult,
  Task,
  UpdateDocumentMetadataInput,
  UploadDocumentInput,
} from "@/api-client/types";
import { request, requestVoid, waitForTask } from "@/api-client/http/client";
import { ApiError } from "@/api-client/http/errors";

export const documentApi: DocumentApi = {
  async listDocuments(params: DocumentListParams = {}) {
    const query = {
      page: params.page ?? 1,
      size: params.size ?? 20,
      reviewStatus: params.reviewStatus,
      ingestStatus: params.ingestStatus,
      sensitivity: params.sensitivity,
      keyword: params.keyword,
      tagId: params.tagId,
    };
    const url = params.kbId != null ? `/kbs/${params.kbId}/documents` : "/documents";
    return request<PageResult<DocumentSummary>>({ method: "GET", url, params: query });
  },

  async getDocument(id: number) {
    return request<DocumentDetail>({ method: "GET", url: `/documents/${id}` });
  },

  async listDocumentVersions(documentId: number) {
    return request<DocumentVersion[]>({ method: "GET", url: `/documents/${documentId}/versions` });
  },

  async uploadDocument(input: UploadDocumentInput) {
    const init = await request<{ uploadId: string }>({
      method: "POST",
      url: "/upload/init",
      data: {
        kbId: input.kbId,
        fileName: input.fileName,
        fileSize: input.fileSize,
        title: input.title,
        sensitivity: input.sensitivity,
      },
    });
    const task = await request<Task>({
      method: "POST",
      url: `/upload/${init.uploadId}/complete`,
      data: {},
    });
    const finished = await waitForTask(task.id);
    const documentId = Number(finished.resourceId ?? task.resourceId);
    if (!Number.isFinite(documentId) || documentId <= 0) {
      throw new ApiError("上传任务未返回文档标识，请稍后在文档列表查看", { code: "E-TASK" });
    }
    return request<DocumentSummary>({ method: "GET", url: `/documents/${documentId}` });
  },

  async updateDocumentMetadata(id: number, input: UpdateDocumentMetadataInput) {
    return request<DocumentDetail>({
      method: "PATCH",
      url: `/documents/${id}`,
      data: {
        title: input.title,
        sensitivity: input.sensitivity,
        tags: input.tags,
        ownerName: input.ownerName,
      },
    });
  },

  async deleteDocument(id: number) {
    await request<Task>({
      method: "POST",
      url: `/documents/${id}/deletion`,
      data: { reason: "前端删除" },
    });
  },

  async retryIngest(id: number) {
    const task = await request<Task>({ method: "POST", url: `/documents/${id}/reparse`, data: {} });
    await waitForTask(task.id);
    return request<DocumentDetail>({ method: "GET", url: `/documents/${id}` });
  },

  async rollbackVersion(id: number, versionNo: number) {
    return request<DocumentDetail>({
      method: "POST",
      url: `/documents/${id}/rollback`,
      data: { versionNo },
    });
  },

  async toggleFavorite(id: number) {
    const detail = await request<DocumentDetail>({ method: "GET", url: `/documents/${id}` });
    const next = !detail.isFavorite;
    if (next) {
      await requestVoid({ method: "POST", url: "/favorites", data: { documentId: id } });
    } else {
      await requestVoid({ method: "DELETE", url: "/favorites", data: { documentId: id } });
    }
    return next;
  },

  async listDocumentAcl(id: number) {
    return request<AclEntry[]>({ method: "GET", url: `/documents/${id}/acl` });
  },

  async setDocumentAcl(id: number, requestBody: AclSetRequest) {
    return request<AclEntry[]>({
      method: "PUT",
      url: `/documents/${id}/acl`,
      data: { entries: requestBody.entries },
    });
  },
};
