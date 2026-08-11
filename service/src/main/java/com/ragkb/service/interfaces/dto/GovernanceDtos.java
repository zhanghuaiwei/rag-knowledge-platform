package com.ragkb.service.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 治理中心域 DTO：元数据 schema / 审核 / 保留与法律保全 / 删除与证明 / 标签
 * （对齐前端 Governance 契约）。
 */
public final class GovernanceDtos {

    private GovernanceDtos() {
    }

    // ---- 元数据 schema（GKB-04） ----

    public record MetadataField(
            @NotBlank String key,
            @NotBlank String label,
            @NotBlank String type,
            boolean required,
            List<String> options) {
    }

    public record MetadataSchema(
            long id,
            String name,
            String description,
            List<MetadataField> fields,
            String status,
            Instant updatedAt) {
    }

    public record MetadataSchemaInput(
            @NotBlank String name,
            String description,
            @NotEmpty List<@NotNull MetadataField> fields) {
    }

    // ---- 内容审核（F2.13） ----

    public record ReviewItem(
            long documentId,
            String title,
            String kbName,
            String submitter,
            String sensitivity,
            String submittedAt,
            long commentCount) {
    }

    public record ReviewActionRequest(@Size(max = 2048) String comment) {
    }

    // ---- 保留策略与法律保全 ----

    public record RetentionPolicy(
            long id,
            String name,
            String appliesTo,
            Long targetId,
            int durationMonths,
            String action,
            boolean enabled,
            Instant createdAt) {
    }

    public record RetentionPolicyInput(
            @NotBlank String name,
            @NotBlank String appliesTo,
            @NotNull Integer durationMonths,
            @NotBlank String action) {
    }

    public record RetentionPolicyToggleRequest(@NotNull Boolean enabled) {
    }

    public record LegalHold(
            long id,
            String name,
            List<Long> documentIds,
            String reason,
            String createdBy,
            Instant createdAt,
            Instant releasedAt) {
    }

    public record LegalHoldInput(
            @NotBlank String name,
            @NotBlank String reason,
            @NotEmpty List<@NotNull Long> documentIds) {
    }

    // ---- 删除审批与删除证明 ----

    public record DeletionProgress(boolean storage, boolean index, boolean cache, boolean backup) {
    }

    public record DeletionTask(
            long id,
            long documentId,
            String fileName,
            String reason,
            String requestedBy,
            String status,
            Instant createdAt,
            DeletionProgress progress) {
    }

    public record DeletionReceipt(
            String id,
            long taskId,
            long documentId,
            String fileName,
            String checksum,
            Instant deletedAt,
            String operator) {
    }
}
