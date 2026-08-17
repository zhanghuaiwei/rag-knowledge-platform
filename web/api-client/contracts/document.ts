import type {
  AclEntry,
  AclSetRequest,
  DocumentDetail,
  DocumentListParams,
  DocumentSummary,
  DocumentVersion,
  PageResult,
  UpdateDocumentMetadataInput,
  UploadDocumentInput,
} from "@/api-client/types";

/** 文档域契约（F2.2 / F2.10 / F2.14）。 */
export interface DocumentApi {
  listDocuments(params?: DocumentListParams): Promise<PageResult<DocumentSummary>>;
  getDocument(id: number): Promise<DocumentDetail>;
  listDocumentVersions(documentId: number): Promise<DocumentVersion[]>;
  uploadDocument(input: UploadDocumentInput): Promise<DocumentSummary>;
  updateDocumentMetadata(id: number, input: UpdateDocumentMetadataInput): Promise<DocumentDetail>;
  deleteDocument(id: number): Promise<void>;
  retryIngest(id: number): Promise<DocumentDetail>;
  rollbackVersion(id: number, versionNo: number): Promise<DocumentDetail>;
  toggleFavorite(id: number): Promise<boolean>;
  listDocumentAcl(id: number): Promise<AclEntry[]>;
  setDocumentAcl(id: number, request: AclSetRequest): Promise<AclEntry[]>;
  /**
   * 预览文档：获取文件字节流并构造浏览器可用的 Blob URL（需 VIEW_CONTENT 权限）。
   * 返回的 URL 用完后应调用 URL.revokeObjectURL 释放。
   */
  previewDocument(id: number): Promise<string>;
  /**
   * 下载文档：获取原始文件字节流并触发浏览器下载（需 DOWNLOAD_ORIGINAL 权限）。
   */
  downloadDocument(id: number, versionId?: number): Promise<void>;
}
