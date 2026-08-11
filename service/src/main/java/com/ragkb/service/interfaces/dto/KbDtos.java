package com.ragkb.service.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 知识库域 DTO（对齐前端 Kb 契约；OpenAPI 草案 Kb 缺 role/members/documentCount 等 UI 必需字段，
 * 此处按产品契约补全）。
 */
public final class KbDtos {

    private KbDtos() {
    }

    public record KbMember(long userId, String userName, String role, Instant joinedAt) {
    }

    public record Kb(
            long id,
            String name,
            String description,
            String visibility,
            String status,
            String role,
            long documentCount,
            long chunkCount,
            String dataRegion,
            String indexProfileName,
            boolean requiresReview,
            boolean ocrEnabled,
            Instant createdAt,
            Instant updatedAt,
            List<KbMember> members) {
    }

    /** 创建知识库入参（对齐前端新建向导字段）。 */
    public record KbCreateRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 1024) String description,
            String visibility,
            String domain,
            String sensitivity,
            String retention,
            String dataRegion,
            String modelPolicy,
            Boolean requiresReview,
            Boolean ocrEnabled) {
    }

    /** 更新知识库入参；status 用于归档等状态变更（产品契约所需，OpenAPI 草案未覆盖）。 */
    public record KbUpdateRequest(
            @Size(max = 128) String name,
            @Size(max = 1024) String description,
            String visibility,
            Boolean requiresReview,
            Boolean ocrEnabled,
            String status) {
    }

    public record KbMemberRequest(
            @NotNull Long userId,
            @NotBlank String role) {
    }

    /** name 为空时服务端自动生成「原名称（副本）」。 */
    public record CloneKbRequest(@Size(max = 128) String name) {
    }

    /** 索引构建任务。 */
    public record IndexBuild(
            long id,
            int profileVersion,
            String status,
            long inputDocuments,
            long chunkCount,
            long failedCount,
            Object qualityGate,
            String errorCode,
            Instant createdAt,
            Instant publishedAt) {
    }
}
