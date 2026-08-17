package com.ragkb.service.modules.connector.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.common.model.Task;
import com.ragkb.service.modules.connector.dto.ConnectorCreateDto;
import com.ragkb.service.modules.connector.dto.ConnectorUpdateDto;
import com.ragkb.service.modules.connector.dto.ConnectorValidateDto;
import com.ragkb.service.modules.connector.dto.SyncDto;
import com.ragkb.service.modules.connector.vo.ConnectorValidateResultVo;
import com.ragkb.service.modules.connector.vo.ConnectorVo;
import com.ragkb.service.modules.connector.service.ConnectorService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内容源连接器接口入口（配置 / 校验 / 同步）。业务实现见 {@link ConnectorService}。
 */
@RestController
@RequestMapping("/api/v1/connections")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping("")
    public ApiResponse<List<ConnectorVo>> listConnectors() {
        return ApiResponse.ok(connectorService.listConnectors());
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConnectorVo> createConnector(
            @Valid @RequestBody ConnectorCreateDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(connectorService.createConnector(request, idempotencyKey));
    }

    @GetMapping("/{connectionId}")
    public ApiResponse<ConnectorVo> getConnector(@PathVariable long connectionId) {
        return ApiResponse.ok(connectorService.getConnector(connectionId));
    }

    @PatchMapping("/{connectionId}")
    public ApiResponse<ConnectorVo> updateConnector(
            @PathVariable long connectionId,
            @Valid @RequestBody ConnectorUpdateDto request) {
        return ApiResponse.ok(connectorService.updateConnector(connectionId, request));
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> deleteConnector(@PathVariable long connectionId) {
        connectorService.deleteConnector(connectionId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{connectionId}/validate")
    public ApiResponse<ConnectorValidateResultVo> validateConnector(
            @PathVariable long connectionId,
            @Valid @RequestBody ConnectorValidateDto request) {
        return ApiResponse.ok(connectorService.validateConnector(request));
    }

    @PostMapping("/{connectionId}/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Task> syncConnector(
            @PathVariable long connectionId,
            @Valid @RequestBody SyncDto request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(connectorService.syncConnector(connectionId, request, idempotencyKey));
    }
}
