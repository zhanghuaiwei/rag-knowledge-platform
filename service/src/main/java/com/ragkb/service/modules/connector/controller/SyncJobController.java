package com.ragkb.service.modules.connector.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.connector.vo.SyncJobVo;
import com.ragkb.service.modules.connector.service.ConnectorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内容源连接器 - 同步任务接口。业务实现见 {@link ConnectorService}。
 */
@RestController
@RequestMapping("/api/v1/sync-jobs")
public class SyncJobController {

    private final ConnectorService connectorService;

    public SyncJobController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping("/{jobId}")
    public ApiResponse<SyncJobVo> getSyncJob(@PathVariable long jobId) {
        return ApiResponse.ok(connectorService.getSyncJob(jobId));
    }

    @PostMapping("/{jobId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<Void> cancelSyncJob(@PathVariable long jobId) {
        connectorService.cancelSyncJob(jobId);
        return ResponseEntity.accepted().build();
    }
}
