package com.ragkb.service.modules.analytics.service;

import com.ragkb.service.modules.analytics.vo.DauPointVo;
import com.ragkb.service.modules.analytics.vo.KnowledgeHealthPointVo;
import com.ragkb.service.modules.analytics.vo.TokenCostPointVo;
import com.ragkb.service.modules.analytics.vo.TopDocumentPointVo;
import com.ragkb.service.modules.analytics.vo.UsagePointVo;

import java.util.List;

/**
 * 用量与质量用例（实现点由人工完成；事实源 usage_daily/cost_record）。
 */
public interface AnalyticsService {

    /** period: DAY / WEEK / MONTH。 */
    List<UsagePointVo> getDailyUsage(String aggregation);

    List<TokenCostPointVo> getTokenCosts(String period);

    List<TopDocumentPointVo> getTopDocuments();

    List<DauPointVo> getDau();

    KnowledgeHealthPointVo getKnowledgeHealth();

    /** CSV 导出：写入 out 流。 */
    void export(String kind, String period, java.io.OutputStream out);
}
