package com.ragkb.service.interfaces;

import com.ragkb.service.application.ConnectorService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.common.Task;
import com.ragkb.service.interfaces.dto.ConnectorDtos.Connector;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorCreateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorUpdateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorValidateRequest;
import com.ragkb.service.interfaces.dto.ConnectorDtos.ConnectorValidateResult;
import com.ragkb.service.interfaces.dto.ConnectorDtos.SyncJob;
import com.ragkb.service.interfaces.dto.ConnectorDtos.SyncRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内容源连接器接口入口（配置 / 校验 / 同步）。业务实现见 {@link ConnectorService}。
 */
@RestController
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping("/api/v1/connections")
    public ApiResponse<List<Connector>> listConnectors() {
        return ApiResponse.ok(connectorService.listConnectors());
    }

    @PostMapping("/api/v1/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Connector> createConnector(
            @Valid @RequestBody ConnectorCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(connectorService.createConnector(request, idempotencyKey));
    }

    @GetMapping("/api/v1/connections/{connectionId}")
    public ApiResponse<Connector> getConnector(@PathVariable long connectionId) {
        return ApiResponse.ok(connectorService.getConnector(connectionId));
    }

    @PatchMapping("/api/v1/connections/{connectionId}")
    public ApiResponse<Connector> updateConnector(
            @PathVariable long connectionId,
            @Valid @RequestBody ConnectorUpdateRequest request) {
        return ApiResponse.ok(connectorService.updateConnector(connectionId, request));
    }

    @DeleteMapping("/api/v1/connections/{connectionId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> deleteConnector(@PathVariable long connectionId) {
        connectorService.deleteConnector(connectionId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/v1/connections/{connectionId}/validate")
    public ApiResponse<ConnectorValidateResult> validateConnector(
            @PathVariable long connectionId,
            @Valid @RequestBody ConnectorValidateRequest request) {
        return ApiResponse.ok(connectorService.validateConnector(request));
    }

    @PostMapping("/api/v1/connections/{connectionId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> syncConnector(
            @PathVariable long connectionId,
            @Valid @RequestBody SyncRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(connectorService.syncConnector(connectionId, request, idempotencyKey));
    }

    @GetMapping("/api/v1/sync-jobs/{jobId}")
    public ApiResponse<SyncJob> getSyncJob(@PathVariable long jobId) {
        return ApiResponse.ok(connectorService.getSyncJob(jobId));
    }

    @PostMapping("/api/v1/sync-jobs/{jobId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> cancelSyncJob(@PathVariable long jobId) {
        connectorService.cancelSyncJob(jobId);
        return ResponseEntity.accepted().build();
    }
}
