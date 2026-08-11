package com.ragkb.service.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 管理中心域 DTO：成员 / 组织 / 审计 / Webhook / 通知（对齐前端 Admin 契约）。
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record User(
            long id,
            String name,
            String email,
            String status,
            String role,
            String orgName,
            Instant lastLoginAt) {
    }

    public record Org(
            long id,
            Long parentId,
            String name,
            String path,
            long memberCount,
            String status) {
    }

    public record OrgInput(@NotBlank @Size(max = 128) String name, Long parentId) {
    }

    public record AuditLogEntry(
            long id,
            String actor,
            String actorType,
            String action,
            String resourceType,
            String resourceId,
            String result,
            String reasonCode,
            String requestId,
            Instant occurredAt) {
    }

    public record Webhook(
            long id,
            String name,
            String targetUrl,
            List<String> eventTypes,
            String status,
            Instant createdAt) {
    }

    public record WebhookInput(
            @NotBlank String name,
            @NotBlank String targetUrl,
            @NotEmpty List<@NotNull String> eventTypes) {
    }

    public record WebhookToggleRequest(@NotNull Boolean paused) {
    }

    public record WebhookDelivery(
            long id,
            long subscriptionId,
            String eventId,
            String status,
            int attempts,
            Instant nextRetryAt,
            String responseSummary) {
    }

    public record NotificationItem(
            long id,
            String kind,
            String level,
            String title,
            String body,
            boolean read,
            Instant createdAt,
            String href) {
    }
}
