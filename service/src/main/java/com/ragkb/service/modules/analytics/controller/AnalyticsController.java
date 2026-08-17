package com.ragkb.service.modules.analytics.controller;

import com.ragkb.service.common.api.ApiResponse;
import com.ragkb.service.modules.analytics.vo.DauPointVo;
import com.ragkb.service.modules.analytics.vo.KnowledgeHealthPointVo;
import com.ragkb.service.modules.analytics.vo.TokenCostPointVo;
import com.ragkb.service.modules.analytics.vo.TopDocumentPointVo;
import com.ragkb.service.modules.analytics.vo.UsagePointVo;
import com.ragkb.service.modules.analytics.service.AnalyticsService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
// 装配条件：实现依赖数据库聚合（ragkb.db.enabled=true）；scaffold 模式下端点整体下线，
// 与 conversation/identity 模块的条件装配约定一致（避免无数据库时上下文装配失败）。
@RestController
@RequestMapping("/api/v1/analytics")
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/usage")
    public ApiResponse<List<UsagePointVo>> getDailyUsage(
            @RequestParam(defaultValue = "DAY") String period) {
        return ApiResponse.ok(analyticsService.getDailyUsage(period));
    }

    @GetMapping("/costs")
    public ApiResponse<List<TokenCostPointVo>> getTokenCosts(
            @RequestParam(defaultValue = "DAY") String period) {
        return ApiResponse.ok(analyticsService.getTokenCosts(period));
    }

    @GetMapping("/top-documents")
    public ApiResponse<List<TopDocumentPointVo>> getTopDocuments() {
        return ApiResponse.ok(analyticsService.getTopDocuments());
    }

    @GetMapping("/dau")
    public ApiResponse<List<DauPointVo>> getDau() {
        return ApiResponse.ok(analyticsService.getDau());
    }

    @GetMapping("/kb-health")
    public ApiResponse<KnowledgeHealthPointVo> getKnowledgeHealth() {
        return ApiResponse.ok(analyticsService.getKnowledgeHealth());
    }

    /** CSV 导出（application/octet-stream）。 */
    @GetMapping("/export")
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
