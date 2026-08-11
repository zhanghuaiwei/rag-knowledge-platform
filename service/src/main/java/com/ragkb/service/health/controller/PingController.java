package com.ragkb.service.health.controller;

import com.ragkb.service.common.api.ApiResponse;

import java.util.Map;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 最小接口适配验证端点（脚手架阶段使用，非业务契约）。
 *
 * <p>真实的 health/liveness/readiness 由 Actuator 提供
 * （{@code /actuator/health} 与探针）。本端点验证 controller → use case 的
 * 空调用链可用，并演示统一响应信封 {@link ApiResponse}。</p>
 */
@RestController
public class PingController {

    @GetMapping("/api/v1/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok(Map.of(
                "service", "ragkb-service",
                "status", "ok",
                "phase", "scaffold"));
    }
}
