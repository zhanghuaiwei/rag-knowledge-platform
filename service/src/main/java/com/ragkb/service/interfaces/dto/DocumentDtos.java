package com.ragkb.service.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 文档域 DTO：文档 / 版本 / 上传 / ACL / 标签 / 收藏（对齐前端 Document 契约）。
 */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentSummary(
            long id,
            long kbId,
            String kbName,
            String title,
            String fileName,
            String fileExt,
            String mimeType,
            String sourceType,
            long fileSize,
            int versionNo,
            String ingestStatus,
            String reviewStatus,
            String sensitivity,
            String ownerName,
            long chunkCount,
            String updatedAt) {
    }

    public record DocumentVersion(
            int versionNo,
            long fileSize,
            String ingestStatus,
            String safetyStatus,
            long chunkCount,
            String createdBy,
            String createdAt) {
    }

    public record DocumentDetail(
            long id,
            long kbId,
            String kbName,
            String title,
            String fileName,
            String fileExt,
            String mimeType,
            String sourceType,
            long fileSize,
            int versionNo,
            String ingestStatus,
            String reviewStatus,
            String sensitivity,
            String ownerName,
            long chunkCount,
            String updatedAt,
            List<DocumentVersion> versions,
            List<String> tags,
            boolean isFavorite,
            Integer retryCount) {
    }

    /** 上传初始化入参（真实实现为分片上传 + 安全扫描 GKB-03）。 */
    public record UploadInitRequest(
            @NotNull Long kbId,
            @NotBlank @Size(max = 256) String fileName,
            @NotNull Long fileSize,
            String mimeType,
            String sha256,
            String title,
            String sensitivity) {
    }

    public record UploadInitResponse(
            String uploadId,
            long partSize,
            int partCount,
            List<Integer> uploadedParts,
            List<String> presignedPutUrls) {
    }

    /** 文档元数据可编辑字段（租户 schema 驱动，最小子集）。 */
    public record UpdateDocumentMetadataRequest(
            String title,
            String sensitivity,
            List<String> tags,
            String ownerName) {
    }

    public record RollbackVersionRequest(@NotNull Integer versionNo) {
    }

    public record DeletionRequest(@NotBlank @Size(max = 2048) String reason) {
    }

    // ---- 文档级权限（F2.14 / GKB-04） ----

    public record AclEntry(long id, String principalType, String principalName, List<String> permissions) {
    }

    public record AclEntryDraft(
            @NotBlank String principalType,
            @NotBlank String principalName,
            @NotEmpty List<String> permissions) {
    }

    /** 覆盖式写入：调用方传入完整目标列表（白名单语义）。 */
    public record AclSetRequest(@NotEmpty List<@NotNull AclEntryDraft> entries) {
    }

    // ---- 标签 / 收藏 ----

    public record Tag(long id, String name, long documentCount) {
    }

    public record CreateTagRequest(@NotBlank @Size(max = 64) String name) {
    }

    public record FavoriteItem(long documentId, String title, String fileName, String kbName, String savedAt) {
    }

    public record FavoriteRequest(@NotNull Long documentId) {
    }
}
