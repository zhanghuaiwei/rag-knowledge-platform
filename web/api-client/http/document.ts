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
  UploadInitResponse,
} from "@/api-client/types";
import { request, requestBlob, requestVoid, waitForTask } from "@/api-client/http/client";
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
    // ① 初始化分片会话：服务端校验目标知识库 / 文件名 / 大小 / 敏感级，返回分片策略
    const init = await request<UploadInitResponse>({
      method: "POST",
      url: "/upload/init",
      data: {
        kbId: input.kbId,
        fileName: input.fileName,
        fileSize: input.fileSize,
        mimeType: input.file.type,
        title: input.title,
        sensitivity: input.sensitivity,
      },
    });
    // ② 逐分片直传原始字节（application/octet-stream）：
    //    partSize=0 表示直传（单分片=整个文件）；partSize>0 按服务端阈值切片，
    //    同号重复 PUT 覆盖 = 幂等续传（对齐 OpenAPI /upload/{id}/parts/{n}）。
    const partCount = Math.max(1, init.partCount);
    for (let n = 1; n <= partCount; n++) {
      const start = init.partSize === 0 ? 0 : (n - 1) * init.partSize;
      const end = init.partSize === 0 ? input.file.size : Math.min(n * init.partSize, input.file.size);
      await requestVoid({
        method: "PUT",
        url: `/upload/${init.uploadId}/parts/${n}`,
        data: input.file.slice(start, end),
        headers: { "Content-Type": "application/octet-stream" },
      });
    }
    // ③ 合并分片 → 写对象存储 → 落库（document/document_version/parse_task/outbox 同事务）
    //    → 进入安全扫描队列；返回 SUCCEEDED 任务，resourceId=documentId 供回读详情。
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

  async previewDocument(id: number) {
    // 预览：获取原始字节流 → Blob URL（iframe / img / PDF.js 直接渲染）
    const blob = await requestBlob({ method: "GET", url: `/documents/${id}/preview` });
    return URL.createObjectURL(blob);
  },

  async downloadDocument(id: number, versionId?: number) {
    // 下载：获取原始字节流 → 触发浏览器下载（attachment）
    const blob = await requestBlob({
      method: "GET",
      url: `/documents/${id}/download`,
      params: versionId != null ? { versionId } : undefined,
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = ""; // 文件名由后端 Content-Disposition 提供，留空让浏览器自动取名
    link.click();
    URL.revokeObjectURL(url);
  },
};
