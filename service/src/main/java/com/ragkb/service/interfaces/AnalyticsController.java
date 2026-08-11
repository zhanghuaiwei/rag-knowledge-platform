package com.ragkb.service.interfaces;

import com.ragkb.service.application.AnalyticsService;
import com.ragkb.service.common.ApiResponse;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.DauPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.KnowledgeHealthPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.TokenCostPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.TopDocumentPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.UsagePoint;
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
    public ApiResponse<List<UsagePoint>> getDailyUsage(
            @RequestParam(defaultValue = "DAY") String period) {
        return ApiResponse.ok(analyticsService.getDailyUsage(period));
    }

    @GetMapping("/api/v1/analytics/costs")
    public ApiResponse<List<TokenCostPoint>> getTokenCosts(
            @RequestParam(defaultValue = "DAY") String period) {
        return ApiResponse.ok(analyticsService.getTokenCosts(period));
    }

    @GetMapping("/api/v1/analytics/top-documents")
    public ApiResponse<List<TopDocumentPoint>> getTopDocuments() {
        return ApiResponse.ok(analyticsService.getTopDocuments());
    }

    @GetMapping("/api/v1/analytics/dau")
    public ApiResponse<List<DauPoint>> getDau() {
        return ApiResponse.ok(analyticsService.getDau());
    }

    @GetMapping("/api/v1/analytics/kb-health")
    public ApiResponse<KnowledgeHealthPoint> getKnowledgeHealth() {
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
