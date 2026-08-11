package com.ragkb.service.modules.analytics.service.impl;

import com.ragkb.service.util.TodoSupport;
import com.ragkb.service.modules.analytics.vo.DauPointVo;
import com.ragkb.service.modules.analytics.vo.KnowledgeHealthPointVo;
import com.ragkb.service.modules.analytics.vo.TokenCostPointVo;
import com.ragkb.service.modules.analytics.vo.TopDocumentPointVo;
import com.ragkb.service.modules.analytics.vo.UsagePointVo;
import com.ragkb.service.modules.analytics.service.AnalyticsService;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;

/**
 * 用量与质量桩实现（实现点由人工替换）。
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    @Override
    public List<UsagePointVo> getDailyUsage(String aggregation) {
        return TodoSupport.notImplemented("AnalyticsService#getDailyUsage");
    }

    @Override
    public List<TokenCostPointVo> getTokenCosts(String period) {
        return TodoSupport.notImplemented("AnalyticsService#getTokenCosts");
    }

    @Override
    public List<TopDocumentPointVo> getTopDocuments() {
        return TodoSupport.notImplemented("AnalyticsService#getTopDocuments");
    }

    @Override
    public List<DauPointVo> getDau() {
        return TodoSupport.notImplemented("AnalyticsService#getDau");
    }

    @Override
    public KnowledgeHealthPointVo getKnowledgeHealth() {
        return TodoSupport.notImplemented("AnalyticsService#getKnowledgeHealth");
    }

    @Override
    public void export(String kind, String period, OutputStream out) {
        TodoSupport.notImplemented("AnalyticsService#export");
    }
}
