package com.ragkb.service.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 内容源连接器域 DTO（对齐前端 Connector 契约；计数/同步模式为产品契约所需）。
 */
public final class ConnectorDtos {

    private ConnectorDtos() {
    }

    public record ConnectorCounts(
            long discovered,
            long created,
            long updated,
            long deleted,
            long failed) {
    }

    public record Connector(
            long id,
            String name,
            String providerKey,
            String syncMode,
            String status,
            Instant lastSuccessAt,
            String lastErrorCode,
            long cursorAgeMin,
            ConnectorCounts counts) {
    }

    public record ConnectorCreateRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String providerKey,
            Map<String, Object> config,
            Boolean enabled) {
    }

    public record ConnectorUpdateRequest(String name, Map<String, Object> config, Boolean enabled) {
    }

    public record ConnectorValidateRequest(@NotBlank String providerKey, @NotNull Map<String, Object> config) {
    }

    public record ConnectorValidateResult(boolean ok, String message) {
    }

    public record SyncRequest(@NotBlank String syncType) {
    }

    public record SyncJob(
            long id,
            long connectionId,
            String syncType,
            String status,
            long discovered,
            List<String> failedObjects,
            Instant lastSuccessAt,
            String errorCode,
            Instant createdAt) {
    }
}
