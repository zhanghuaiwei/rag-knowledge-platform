import type { DocumentApi } from "@/api-client/contracts/document";
import type {
  AclEntry,
  AclSetRequest,
  DocumentDetail,
  DocumentListParams,
  DocumentSummary,
  DocumentVersion,
  UpdateDocumentMetadataInput,
  UploadDocumentInput,
} from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";
import { appendAudit, nextId, now } from "@/mocks/helpers";

function notFound(resource: string): never {
  throw new Error(`${resource}不存在`);
}

function requireDoc(id: number): DocumentDetail {
  const detail = db.getDocumentDetail(id);
  if (!detail) notFound("文档");
  return detail;
}

export const documentApi: DocumentApi = {
  async listDocuments(params: DocumentListParams = {}) {
    await delay();
    const keyword = params.keyword?.trim().toLowerCase();
    let items = db.documents;
    if (params.kbId) items = items.filter((doc) => doc.kbId === params.kbId);
    if (params.ingestStatus) items = items.filter((doc) => doc.ingestStatus === params.ingestStatus);
    if (params.reviewStatus) items = items.filter((doc) => doc.reviewStatus === params.reviewStatus);
    if (params.sensitivity) items = items.filter((doc) => doc.sensitivity === params.sensitivity);
    if (params.tagId !== undefined) {
      items = items.filter((doc) => (db.getDocumentDetail(doc.id)?.tags ?? []).includes(String(params.tagId)));
    }
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
    return requireDoc(id);
  },

  async listDocumentVersions(documentId: number) {
    await delay(120);
    return requireDoc(documentId).versions;
  },

  async uploadDocument(input: UploadDocumentInput) {
    await delay(500);
    const kb = db.kbs.find((item) => item.id === input.kbId);
    if (!kb) notFound("知识库");
    const fileExt = input.fileName.split(".").pop()?.toLowerCase() ?? "";
    const id = nextId(db.documents);
    const summary: DocumentSummary = {
      id,
      kbId: kb.id,
      kbName: kb.name,
      title: input.title,
      fileName: input.fileName,
      fileExt,
      mimeType: "application/octet-stream",
      sourceType: "UPLOAD",
      fileSize: input.fileSize,
      versionNo: 1,
      ingestStatus: "PARSING",
      reviewStatus: "DRAFT",
      sensitivity: input.sensitivity,
      ownerName: db.currentUser.name,
      chunkCount: 0,
      updatedAt: now(),
    };
    db.addDocument(summary);
    // 知识库计数同步
    kb.documentCount += 1;
    appendAudit({ action: "document.upload", resourceType: "DOCUMENT", resourceId: id });
    return summary;
  },

  async updateDocumentMetadata(id: number, input: UpdateDocumentMetadataInput) {
    await delay(300);
    const detail = requireDoc(id);
    if (input.title !== undefined) detail.title = input.title;
    if (input.sensitivity !== undefined) detail.sensitivity = input.sensitivity;
    if (input.ownerName !== undefined) detail.ownerName = input.ownerName;
    if (input.tags !== undefined) detail.tags = input.tags;
    detail.updatedAt = now();
    return db.updateDocument(id, detail);
  },

  async deleteDocument(id: number) {
    await delay(400);
    const detail = requireDoc(id);
    // mock 直接执行逻辑删除并生成证明；真实实现已发布文档需先撤回或提交删除审批（GKB-04）
    db.removeDocument(id);
    // 同步清理收藏与搜索命中
    db.favorites = db.favorites.filter((item) => item.documentId !== id);
    db.searchItems = db.searchItems.filter((item) => item.documentId !== id);
    // 生成删除任务与证明
    const taskId = nextId(db.deletionTasks);
    db.deletionTasks.unshift({
      id: taskId,
      documentId: id,
      fileName: detail.fileName,
      reason: "手动删除",
      requestedBy: db.currentUser.name,
      status: "SUCCEEDED",
      createdAt: now(),
      progress: { storage: true, index: true, cache: true, backup: true },
    });
    db.deletionReceipts.unshift({
      id: nextId(db.deletionReceipts),
      taskId,
      documentId: id,
      fileName: detail.fileName,
      checksum: `sha256:${Math.random().toString(36).slice(2, 14)}`,
      deletedAt: now(),
      operator: db.currentUser.name,
    });
    appendAudit({ action: "document.delete", resourceType: "DOCUMENT", resourceId: id });
  },

  async retryIngest(id: number) {
    await delay(400);
    const detail = requireDoc(id);
    const retryCount = detail.retryCount ?? 0;
    if (retryCount >= 3) {
      throw new Error("解析失败已重试 3 次，请检查内容或联系管理员");
    }
    detail.retryCount = retryCount + 1;
    detail.ingestStatus = "READY";
    detail.reviewStatus = detail.reviewStatus === "DRAFT" ? "PENDING_REVIEW" : detail.reviewStatus;
    detail.chunkCount = Math.max(1, Math.round(detail.fileSize / 60_000));
    detail.updatedAt = now();
    appendAudit({ action: "document.retry", resourceType: "DOCUMENT", resourceId: id });
    return db.updateDocument(id, detail);
  },

  async rollbackVersion(id: number, versionNo: number) {
    await delay(400);
    const detail = requireDoc(id);
    const target = detail.versions.find((v) => v.versionNo === versionNo);
    if (!target) throw new Error("目标版本不存在");
    const newNo = Math.max(...detail.versions.map((v) => v.versionNo)) + 1;
    const snapshot: DocumentVersion = {
      versionNo: newNo,
      fileSize: target.fileSize,
      ingestStatus: "READY",
      safetyStatus: "PASSED",
      chunkCount: target.chunkCount,
      createdBy: db.currentUser.name,
      createdAt: now(),
    };
    detail.versions.push(snapshot);
    detail.versionNo = newNo;
    detail.chunkCount = target.chunkCount;
    detail.ingestStatus = "READY";
    detail.updatedAt = now();
    appendAudit({ action: "document.version.rollback", resourceType: "DOCUMENT", resourceId: id });
    return db.updateDocument(id, detail);
  },

  async toggleFavorite(id: number) {
    await delay(150);
    const detail = requireDoc(id);
    const next = !detail.isFavorite;
    detail.isFavorite = next;
    if (next) {
      db.favorites.unshift({
        documentId: id,
        title: detail.title,
        fileName: detail.fileName,
        kbName: detail.kbName,
        savedAt: now(),
      });
    } else {
      db.favorites = db.favorites.filter((item) => item.documentId !== id);
    }
    return next;
  },

  async listDocumentAcl(id: number) {
    await delay(120);
    requireDoc(id);
    return db.documentAcl[id] ?? [];
  },

  async setDocumentAcl(id: number, request: AclSetRequest) {
    await delay(300);
    requireDoc(id);
    const entries: AclEntry[] = request.entries.map((entry, index) => ({
      id: index + 1,
      principalType: entry.principalType,
      principalName: entry.principalName,
      permissions: [...entry.permissions],
    }));
    db.documentAcl[id] = entries;
    appendAudit({ action: "document.acl.update", resourceType: "DOCUMENT", resourceId: id });
    return entries;
  },
};
