package com.ragkb.service.modules.governance.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.governance.vo.DeletionReceiptVo;
import com.ragkb.service.modules.governance.vo.DeletionTaskVo;
import com.ragkb.service.modules.governance.service.GovernanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 治理中心 - 删除审批与删除证明接口。业务实现见 {@link GovernanceService}。
 */
@RestController
@RequestMapping("/api/v1")
public class DeletionController {

    private final GovernanceService governanceService;

    public DeletionController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @GetMapping("/deletion-tasks")
    public ApiResponse<List<DeletionTaskVo>> listDeletionTasks() {
        return ApiResponse.ok(governanceService.listDeletionTasks());
    }

    @GetMapping("/deletion-tasks/{taskId}")
    public ApiResponse<DeletionTaskVo> getDeletionTask(@PathVariable long taskId) {
        return ApiResponse.ok(governanceService.listDeletionTasks().stream()
                .filter(task -> task.id() == taskId)
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.exception.ApiException(
                        com.ragkb.service.common.exception.ErrorCode.NOT_FOUND, "删除任务不存在")));
    }

    /** 审批删除（产品契约所需；OpenAPI 草案未覆盖）。 */
    @PostMapping("/deletion-tasks/{taskId}/approve")
    public ApiResponse<DeletionTaskVo> approveDeletion(@PathVariable long taskId) {
        return ApiResponse.ok(governanceService.approveDeletion(taskId));
    }

    @GetMapping("/deletion-receipts")
    public ApiResponse<List<DeletionReceiptVo>> listDeletionReceipts() {
        return ApiResponse.ok(governanceService.listDeletionReceipts());
    }

    @GetMapping("/deletion-receipts/{receiptId}")
    public ApiResponse<DeletionReceiptVo> getDeletionReceipt(@PathVariable String receiptId) {
        return ApiResponse.ok(governanceService.listDeletionReceipts().stream()
                .filter(receipt -> receipt.id().equals(receiptId))
                .findFirst()
                .orElseThrow(() -> new com.ragkb.service.common.exception.ApiException(
                        com.ragkb.service.common.exception.ErrorCode.NOT_FOUND, "删除证明不存在")));
    }
}
