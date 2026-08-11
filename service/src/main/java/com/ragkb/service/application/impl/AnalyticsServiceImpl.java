package com.ragkb.service.application.impl;

import com.ragkb.service.application.AnalyticsService;
import com.ragkb.service.application.NotYetImplemented;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.DauPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.KnowledgeHealthPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.TokenCostPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.TopDocumentPoint;
import com.ragkb.service.interfaces.dto.AnalyticsDtos.UsagePoint;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;

/**
 * 用量与质量桩实现（实现点由人工替换）。
 */
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    @Override
    public List<UsagePoint> getDailyUsage(String aggregation) {
        return NotYetImplemented.stub("AnalyticsService#getDailyUsage");
    }

    @Override
    public List<TokenCostPoint> getTokenCosts(String period) {
        return NotYetImplemented.stub("AnalyticsService#getTokenCosts");
    }

    @Override
    public List<TopDocumentPoint> getTopDocuments() {
        return NotYetImplemented.stub("AnalyticsService#getTopDocuments");
    }

    @Override
    public List<DauPoint> getDau() {
        return NotYetImplemented.stub("AnalyticsService#getDau");
    }

    @Override
    public KnowledgeHealthPoint getKnowledgeHealth() {
        return NotYetImplemented.stub("AnalyticsService#getKnowledgeHealth");
    }

    @Override
    public void export(String kind, String period, OutputStream out) {
        NotYetImplemented.stub("AnalyticsService#export");
    }
}
