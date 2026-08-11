package com.ragkb.service.modules.analytics.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.analytics.vo.DauPointVo;
import com.ragkb.service.modules.analytics.vo.KnowledgeHealthPointVo;
import com.ragkb.service.modules.analytics.vo.TokenCostPointVo;
import com.ragkb.service.modules.analytics.vo.TopDocumentPointVo;
import com.ragkb.service.modules.analytics.vo.UsagePointVo;
import com.ragkb.service.modules.analytics.service.AnalyticsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 用量与质量接口入口。业务实现见 {@link AnalyticsService}。
 *
 * <p>OpenAPI 草案的 usage/costs 为聚合视图，前端需要按日明细渲染图表，
 * 故返回明细点（top-documents / dau 为产品契约所需新增端点）。
 */
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/v1/analytics/usage")
    public ApiResponse<List<UsagePointVo>> getDailyUsage(
            @RequestParam(defaultValue = "DAY") String period) {
        return ApiResponse.ok(analyticsService.getDailyUsage(period));
    }

    @GetMapping("/api/v1/analytics/costs")
    public ApiResponse<List<TokenCostPointVo>> getTokenCosts(
            @RequestParam(defaultValue = "DAY") String period) {
        return ApiResponse.ok(analyticsService.getTokenCosts(period));
    }

    @GetMapping("/api/v1/analytics/top-documents")
    public ApiResponse<List<TopDocumentPointVo>> getTopDocuments() {
        return ApiResponse.ok(analyticsService.getTopDocuments());
    }

    @GetMapping("/api/v1/analytics/dau")
    public ApiResponse<List<DauPointVo>> getDau() {
        return ApiResponse.ok(analyticsService.getDau());
    }

    @GetMapping("/api/v1/analytics/kb-health")
    public ApiResponse<KnowledgeHealthPointVo> getKnowledgeHealth() {
        return ApiResponse.ok(analyticsService.getKnowledgeHealth());
    }

    /** CSV 导出（application/octet-stream）。 */
    @GetMapping("/api/v1/analytics/export")
    public void export(
            @RequestParam String kind,
            @RequestParam String period,
            HttpServletResponse response) throws IOException {
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename=\"" + kind + "-" + period + ".csv\"");
        analyticsService.export(kind, period, response.getOutputStream());
    }
}
