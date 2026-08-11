package com.ragkb.service.application;

import com.ragkb.service.interfaces.dto.AnalyticsDtos.DauPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.KnowledgeHealthPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.TokenCostPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.TopDocumentPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.UsagePoint;

import java.util.List;

/**
 * 用量与质量用例（实现点由人工完成；事实源 usage_daily/cost_record）。
 */
public interface AnalyticsService {

    /** period: DAY / WEEK / MONTH。 */
    List<UsagePoint> getDailyUsage(String aggregation);

    List<TokenCostPoint> getTokenCosts(String period);

    List<TopDocumentPoint> getTopDocuments();

    List<DauPoint> getDau();

    KnowledgeHealthPoint getKnowledgeHealth();

    /** CSV 导出：写入 out 流。 */
    void export(String kind, String period, java.io.OutputStream out);
}
