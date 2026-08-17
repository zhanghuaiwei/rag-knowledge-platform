package com.ragkb.service.modules.analytics.service.impl;

import com.ragkb.service.common.exception.ApiException;
import com.ragkb.service.common.exception.ErrorCode;
import com.ragkb.service.modules.analytics.persistence.mapper.CostRecordMapper;
import com.ragkb.service.modules.analytics.persistence.query.ActiveUserBucketRow;
import com.ragkb.service.modules.analytics.persistence.query.AnswerQualityRow;
import com.ragkb.service.modules.analytics.persistence.query.DailyCostBucketRow;
import com.ragkb.service.modules.analytics.persistence.query.DocFreshnessRow;
import com.ragkb.service.modules.analytics.persistence.query.ModelCostRow;
import com.ragkb.service.modules.analytics.persistence.query.TopDocumentRow;
import com.ragkb.service.modules.analytics.persistence.query.UsageBucketRow;
import com.ragkb.service.modules.analytics.vo.DauPointVo;
import com.ragkb.service.modules.analytics.vo.KnowledgeHealthPointVo;
import com.ragkb.service.modules.analytics.vo.TokenCostPointVo;
import com.ragkb.service.modules.analytics.vo.TopDocumentPointVo;
import com.ragkb.service.modules.analytics.vo.UsagePointVo;
import com.ragkb.service.modules.analytics.persistence.mapper.AnalyticsStatsMapper;
import com.ragkb.service.modules.analytics.service.AnalyticsService;
import com.ragkb.service.modules.identity.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 用量与质量用例实现。
 *
 * <p>事实源与口径（全部 SQL 聚合，服务层只做合并/除法/空值兜底，不造假数据）：
 * <ul>
 *   <li>问答用量 / DAU / 质量比率：{@code chat_message}（一条 ASSISTANT 回答计一次问答），
 *       活跃用户经 {@code chat_session} 取 user_id；</li>
 *   <li>Token 与成本：{@code cost_record}（全场景 EMBEDDING/RERANK/LLM/OCR，当前无写入方时返回真实空结果）；</li>
 *   <li>热门文档：{@code chat_message_source}（回答引用来源）JOIN {@code document}/{@code kb}；</li>
 *   <li>文档新鲜度：{@code document}（ACTIVE 在库文档近 90 天更新占比）。</li>
 * </ul>
 *
 * <p>多租户：所有查询均以当前认证主体租户过滤（未认证/dev/API Key 主体兜底默认租户 1，
 * 与 {@code KbServiceImpl} 约定一致）；手写 SQL 同时显式 {@code del_flag = 0}。
 */
// 装配条件：统计依赖数据库聚合查询，仅在 ragkb.db.enabled=true（数据库模式）下装配；
// 无数据库的 scaffold 模式下本 Bean 不注册，对应端点由条件装配整体下线（与 conversation 模块约定一致）。
@Service
@ConditionalOnProperty(name = "ragkb.db.enabled", havingValue = "true")
public class AnalyticsServiceImpl implements AnalyticsService {

    /** 统计桶使用的业务时区：TIMESTAMPTZ 先转本地时间再截断，保证「按日」边界正确。 */
    private static final String BIZ_TIME_ZONE = "Asia/Shanghai";

    /** 按日窗口（DAY）：近 14 天，对齐前端「14 天问答总量」文案。 */
    private static final int USAGE_WINDOW_DAYS = 14;

    /** 按周窗口（WEEK）：近 12 周。 */
    private static final int USAGE_WINDOW_WEEKS = 12;

    /** 按月窗口（MONTH）：近 12 个月（按 365 天近似）。 */
    private static final int USAGE_WINDOW_MONTHS = 12;

    /** 成本按日窗口（DAY）：近 30 天（成本视角比用量拉长，便于观察月内趋势）。 */
    private static final int COST_WINDOW_DAYS = 30;

    /** DAU 固定窗口：近 14 天。 */
    private static final int DAU_WINDOW_DAYS = 14;

    /** 热门文档统计窗口：近 30 天。 */
    private static final int TOP_DOC_WINDOW_DAYS = 30;

    /** 热门文档返回条数：TOP 10。 */
    private static final int TOP_DOC_LIMIT = 10;

    /** 质量比率统计窗口：近 30 天回答。 */
    private static final int HEALTH_WINDOW_DAYS = 30;

    /** 文档新鲜度窗口：近 90 天内有更新视为「新鲜」。 */
    private static final int FRESHNESS_WINDOW_DAYS = 90;

    /** CSV 导出支持的类型白名单（与前端 analytics 页各卡片一一对应）。 */
    private static final Set<String> EXPORT_KINDS = Set.of("usage", "costs", "top-documents", "dau");

    /** analytics 读模型聚合（chat_message 用量/活跃/质量、chat_message_source 热门文档、document 新鲜度）。 */
    @Autowired
    private AnalyticsStatsMapper analyticsStatsMapper;

    /** cost_record 聚合查询（按模型成本 / 按桶成本）。 */
    @Autowired
    private CostRecordMapper costRecordMapper;

    /**
     * 按日/周/月聚合问答用量（前端「质量与用量」页主图表）。
     *
     * <p>流程：白名单归一化聚合维度 → 按当前租户并行取三个同口径聚合
     * （问答用量 / 去重活跃用户 / 真实成本）→ 以桶日期做并集对齐合并（TreeMap 升序输出）。
     * searchCount 当前无独立搜索日志事实源表，恒为 0（真实空，不造假）。
     */
    @Override
    public List<UsagePointVo> getDailyUsage(String aggregation) {
        // 聚合桶白名单校验：DAY/WEEK/MONTH → day/week/month，非法值直接 400（防脏值进 SQL）
        String bucket = normalizeBucket(aggregation);
        // 多租户：取当前认证主体租户
        long tenantId = currentTenantId();
        Instant now = Instant.now();
        // 用量与活跃用户同窗口（保证同桶可比）；成本窗口独立（DAY 拉长到 30 天）
        Instant usageSince = now.minus(Duration.ofDays(usageWindowDays(bucket)));
        Instant costSince = now.minus(Duration.ofDays(costWindowDays(bucket)));

        // 事实源一：问答用量桶（仅 ASSISTANT 回答计一次问答）
        List<UsageBucketRow> usageRows =
                analyticsStatsMapper.selectUsageBuckets(tenantId, usageSince, bucket, BIZ_TIME_ZONE);
        // 事实源二：活跃用户桶（消息 JOIN 会话取 user_id 去重）
        List<ActiveUserBucketRow> userRows =
                analyticsStatsMapper.selectActiveUserBuckets(tenantId, usageSince, bucket, BIZ_TIME_ZONE);
        // 事实源三：真实成本桶（cost_record 无数据时为空列表，对应桶成本记 0）
        List<DailyCostBucketRow> costRows =
                costRecordMapper.selectDailyCosts(tenantId, costSince, bucket, BIZ_TIME_ZONE);

        // 三个来源按桶日期索引，便于 O(1) 对齐合并
        Map<String, UsageBucketRow> usageByDate = new HashMap<>();
        for (UsageBucketRow row : usageRows) {
            usageByDate.put(row.getStatDate(), row); // 桶日期 → 用量行
        }
        Map<String, ActiveUserBucketRow> usersByDate = new HashMap<>();
        for (ActiveUserBucketRow row : userRows) {
            usersByDate.put(row.getStatDate(), row); // 桶日期 → 活跃用户行
        }
        Map<String, DailyCostBucketRow> costByDate = new HashMap<>();
        for (DailyCostBucketRow row : costRows) {
            costByDate.put(row.getStatDate(), row); // 桶日期 → 成本行
        }

        // 桶日期并集（TreeSet 升序）：任一来源有数据的日期都出点，无数据来源补 0
        TreeSet<String> dates = new TreeSet<>();
        dates.addAll(usageByDate.keySet());
        dates.addAll(usersByDate.keySet());
        dates.addAll(costByDate.keySet());

        // 逐日组装 VO（缺失来源按 0 兜底，保证字段完整可渲染）
        List<UsagePointVo> points = new ArrayList<>(dates.size());
        for (String date : dates) {
            UsageBucketRow usage = usageByDate.get(date);            // 该桶用量行（可空）
            ActiveUserBucketRow users = usersByDate.get(date);       // 该桶活跃用户行（可空）
            DailyCostBucketRow cost = costByDate.get(date);          // 该桶成本行（可空）
            points.add(new UsagePointVo(
                    date,                                           // 桶起始日 YYYY-MM-DD
                    0L,                                             // 搜索量：无搜索日志事实源，真实为 0
                    usage == null ? 0L : nz(usage.getQaCount()),     // 问答量（回答条数）
                    usage == null ? 0L : nz(usage.getNoAnswerCount()), // 无答案量
                    usage == null ? 0L : nz(usage.getLowConfCount()),  // 低置信量
                    usage == null ? 0L : nz(usage.getTokenIn()),     // 输入 token 合计
                    usage == null ? 0L : nz(usage.getTokenOut()),    // 输出 token 合计
                    users == null ? 0L : nz(users.getActiveUsers()), // 该桶去重活跃用户
                    cost == null || cost.getCost() == null           // 该桶真实成本（无记录记 0）
                            ? 0.0 : cost.getCost().doubleValue()));
        }
        return points;
    }

    /**
     * 按模型聚合 Token 与成本（前端「按模型的 Token 与成本」表格）。
     *
     * <p>事实源 cost_record；表为空（当前无写入方）时返回真实空列表，不造假数据。
     */
    @Override
    public List<TokenCostPointVo> getTokenCosts(String period) {
        // 复用 period 白名单校验，决定统计窗口
        String bucket = normalizeBucket(period);
        long tenantId = currentTenantId(); // 多租户过滤
        Instant since = Instant.now().minus(Duration.ofDays(costWindowDays(bucket)));

        // SQL 按模型聚合（成本降序），服务层仅做行 → VO 映射
        List<ModelCostRow> rows = costRecordMapper.selectModelCosts(tenantId, since);
        List<TokenCostPointVo> result = new ArrayList<>(rows.size());
        for (ModelCostRow row : rows) {
            result.add(new TokenCostPointVo(
                    row.getModelName(),                              // 模型名
                    nz(row.getTokenIn()),                            // 输入 token 合计
                    nz(row.getTokenOut()),                           // 输出 token 合计
                    row.getCost() == null ? 0.0 : row.getCost().doubleValue(), // 成本合计
                    nz(row.getCalls())));                            // 计费调用次数
        }
        return result;
    }

    /**
     * 热门文档 TOP 10（近 30 天被回答引用最多）。
     *
     * <p>事实源 chat_message_source；qaCount 按去重回答条数、searchCount 按来源命中行数。
     */
    @Override
    public List<TopDocumentPointVo> getTopDocuments() {
        long tenantId = currentTenantId(); // 多租户过滤
        Instant since = Instant.now().minus(Duration.ofDays(TOP_DOC_WINDOW_DAYS)); // 近 30 天窗口

        // SQL 聚合 + JOIN 回填文档名/知识库名，服务层仅映射
        List<TopDocumentRow> rows = analyticsStatsMapper.selectTopDocuments(tenantId, since, TOP_DOC_LIMIT);
        List<TopDocumentPointVo> result = new ArrayList<>(rows.size());
        for (TopDocumentRow row : rows) {
            result.add(new TopDocumentPointVo(
                    nz(row.getDocumentId()),                          // 文档 id（前端跳详情用）
                    row.getFileName(),                               // 文件名展示
                    row.getKbName(),                                 // 所属知识库名（库删则为 null）
                    nz(row.getQaCount()),                            // 引用回答条数
                    nz(row.getSearchCount())));                      // 来源命中总次数
        }
        return result;
    }

    /**
     * 日活跃用户（DAU，近 14 天）：按日去重发过消息的用户数。
     */
    @Override
    public List<DauPointVo> getDau() {
        long tenantId = currentTenantId(); // 多租户过滤
        Instant since = Instant.now().minus(Duration.ofDays(DAU_WINDOW_DAYS)); // 近 14 天窗口

        // 复用活跃用户桶查询（固定 day 桶，业务时区切日）
        List<ActiveUserBucketRow> rows =
                analyticsStatsMapper.selectActiveUserBuckets(tenantId, since, "day", BIZ_TIME_ZONE);
        List<DauPointVo> result = new ArrayList<>(rows.size());
        for (ActiveUserBucketRow row : rows) {
            result.add(new DauPointVo(row.getStatDate(), nz(row.getActiveUsers()))); // 日期 + 去重用户数
        }
        return result;
    }

    /**
     * 知识库健康度（租户级四指标，对齐契约 VO 字段）。
     *
     * <p>口径：无答案率/低置信率/平均置信度取近 30 天 ASSISTANT 回答；
     * freshnessScore 取 ACTIVE 在库文档中近 90 天有更新的占比。分母为 0 时各指标兜底 0。
     */
    @Override
    public KnowledgeHealthPointVo getKnowledgeHealth() {
        long tenantId = currentTenantId(); // 多租户过滤
        Instant answerSince = Instant.now().minus(Duration.ofDays(HEALTH_WINDOW_DAYS)); // 质量窗口
        Instant freshSince = Instant.now().minus(Duration.ofDays(FRESHNESS_WINDOW_DAYS)); // 新鲜度窗口

        // 事实源一：近 30 天回答质量汇总（总数/无答案/低置信/平均置信度）
        AnswerQualityRow quality = analyticsStatsMapper.selectAnswerQuality(tenantId, answerSince);
        // 事实源二：在库文档总数与近 90 天更新数
        DocFreshnessRow freshness = analyticsStatsMapper.selectDocFreshness(tenantId, freshSince);

        // 无数据的极端兜底（空表时聚合查询也可能返回 null 行）
        long totalAnswers = quality == null ? 0L : nz(quality.getTotalAnswers());
        long noAnswerCount = quality == null ? 0L : nz(quality.getNoAnswerCount());
        long lowConfCount = quality == null ? 0L : nz(quality.getLowConfCount());
        long totalDocs = freshness == null ? 0L : nz(freshness.getTotalDocs());
        long freshDocs = freshness == null ? 0L : nz(freshness.getFreshDocs());

        // 比率计算：分母为 0（窗口内无问答/无文档）时记 0，避免除零
        double noAnswerRate = totalAnswers == 0 ? 0.0 : (double) noAnswerCount / totalAnswers; // 无答案率
        double lowConfRate = totalAnswers == 0 ? 0.0 : (double) lowConfCount / totalAnswers;   // 低置信率
        // 平均置信度：无回答时 AVG 为 null，兜底 0
        double averageConfidence = quality == null || quality.getAvgConfidence() == null
                ? 0.0 : quality.getAvgConfidence().doubleValue();
        // 新鲜度：近 90 天更新文档占比（0~1，越高知识越新）
        double freshnessScore = totalDocs == 0 ? 0.0 : (double) freshDocs / totalDocs;

        return new KnowledgeHealthPointVo(noAnswerRate, lowConfRate, averageConfidence, freshnessScore);
    }

    /**
     * CSV 流式导出：按 kind 复用上述查询（口径与页面一致），逐行写入响应流。
     *
     * <p>UTF-8 带 BOM（Excel 打开中文表头不乱码）；行分隔符 CRLF（RFC 4180）；
     * 字段含逗号/引号/换行时按 RFC 4180 转义。流由容器管理，这里只 flush 不 close。
     */
    @Override
    public void export(String kind, String period, OutputStream out) {
        // 先校验 kind 白名单，非法值直接 400（未写入任何字节，响应体保持干净）
        if (kind == null || !EXPORT_KINDS.contains(kind)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "不支持的导出类型: " + kind);
        }
        // 包装为带缓冲的 UTF-8 字符流（不关闭底层 OutputStream，由 Servlet 容器管理）
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        try {
            writer.write('\uFEFF'); // UTF-8 BOM：Excel 双击打开时正确识别中文
            switch (kind) {
                // 用量明细：与主图表同口径（period 语义同 getDailyUsage）
                case "usage" -> {
                    writeCsvRow(writer, "日期", "搜索量", "问答量", "无答案量", "低置信量",
                            "输入Token", "输出Token", "活跃用户", "成本"); // 表头
                    for (UsagePointVo point : getDailyUsage(period)) {     // 复用查询，口径一致
                        writeCsvRow(writer, point.date(), point.searchCount(), point.qaCount(),
                                point.noAnswerCount(), point.lowConfCount(), point.tokenIn(),
                                point.tokenOut(), point.activeUsers(), point.cost()); // 数据行
                    }
                }
                // 模型成本：与 Token 成本表格同口径
                case "costs" -> {
                    writeCsvRow(writer, "模型", "调用次数", "输入Token", "输出Token", "成本"); // 表头
                    for (TokenCostPointVo point : getTokenCosts(period)) { // 复用查询
                        writeCsvRow(writer, point.modelName(), point.calls(), point.tokenIn(),
                                point.tokenOut(), point.cost()); // 数据行
                    }
                }
                // 热门文档：与热门文档卡片同口径（无 period 参数）
                case "top-documents" -> {
                    writeCsvRow(writer, "文档ID", "文件名", "知识库", "问答引用", "搜索命中"); // 表头
                    for (TopDocumentPointVo point : getTopDocuments()) {   // 复用查询
                        writeCsvRow(writer, point.documentId(), point.fileName(),
                                point.kbName(), point.qaCount(), point.searchCount()); // 数据行
                    }
                }
                // DAU：与活跃用户图同口径（无 period 参数）
                case "dau" -> {
                    writeCsvRow(writer, "日期", "活跃用户");               // 表头
                    for (DauPointVo point : getDau()) {                    // 复用查询
                        writeCsvRow(writer, point.date(), point.activeUsers()); // 数据行
                    }
                }
                default -> throw new ApiException(ErrorCode.BAD_REQUEST, "不支持的导出类型: " + kind); // 理论不可达（白名单已挡）
            }
            writer.flush(); // 缓冲落盘到响应流（导出为一次性小结果集，flush 即完成「流式」写出）
        } catch (IOException e) {
            // 响应流写出失败（客户端中断等）统一转内部错误，避免堆栈泄漏
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "CSV 导出失败");
        }
    }

    // ------------------------------------------------------------------
    // 私有辅助：维度归一 / 窗口换算 / 租户上下文 / CSV 行写出
    // ------------------------------------------------------------------

    /**
     * period 白名单归一化：DAY/WEEK/MONTH → date_trunc 桶名 day/week/month。
     * null 视为默认 DAY（Controller defaultValue 同语义）；非法值抛 400。
     */
    private static String normalizeBucket(String period) {
        String normalized = period == null ? "DAY" : period.trim().toUpperCase(Locale.ROOT); // 统一大写
        return switch (normalized) {
            case "DAY" -> "day";     // 按日桶
            case "WEEK" -> "week";   // 按周桶（周一为桶起点）
            case "MONTH" -> "month"; // 按月桶（月初为桶起点）
            default -> throw new ApiException(ErrorCode.BAD_REQUEST, "period 仅支持 DAY/WEEK/MONTH");
        };
    }

    /** 用量查询窗口（天）：DAY 近 14 天 / WEEK 近 12 周 / MONTH 近 12 个月。 */
    private static int usageWindowDays(String bucket) {
        return switch (bucket) {
            case "day" -> USAGE_WINDOW_DAYS;        // 按日：14 天
            case "week" -> USAGE_WINDOW_WEEKS * 7;  // 按周：12 周
            case "month" -> USAGE_WINDOW_MONTHS * 30; // 按月：12 个月（按 30 天/月近似）
            default -> USAGE_WINDOW_DAYS;           // 理论不可达（normalizeBucket 已白名单）
        };
    }

    /** 成本查询窗口（天）：DAY 近 30 天 / WEEK 近 12 周 / MONTH 近 12 个月。 */
    private static int costWindowDays(String bucket) {
        return switch (bucket) {
            case "day" -> COST_WINDOW_DAYS;           // 按日：30 天
            case "week" -> USAGE_WINDOW_WEEKS * 7;    // 按周：12 周
            case "month" -> USAGE_WINDOW_MONTHS * 30; // 按月：12 个月
            default -> COST_WINDOW_DAYS;              // 理论不可达
        };
    }

    /** 当前租户 id：JWT 主体携带 tenantId；dev/API Key/未认证兜底默认租户 1（与 KbServiceImpl 一致）。 */
    private long currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // 当前认证上下文
        if (authentication != null
                && authentication.getPrincipal() instanceof TokenService.JwtPrincipal principal // JWT 主体
                && principal.tenantId() > 0) { // 有效租户 id
            return principal.tenantId();
        }
        return 1L; // 默认租户（种子数据 sys_tenant(id=1)）
    }

    /** Long 空安全转 long：SQL COALESCE 已兜底，这里双保险防 NPE。 */
    private static long nz(Long value) {
        return value == null ? 0L : value;
    }

    /** 写出一行 CSV（逗号分隔 + CRLF 结尾），单元格按 RFC 4180 转义。 */
    private static void writeCsvRow(Writer writer, Object... cells) throws IOException {
        StringBuilder line = new StringBuilder(); // 行缓冲
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(','); // 字段分隔符
            }
            // null 单元格写空串，其余取字符串形式
            line.append(csvCell(cells[i] == null ? "" : String.valueOf(cells[i])));
        }
        line.append('\r'); // CRLF 行尾（RFC 4180）
        line.append('\n');
        writer.write(line.toString()); // 整行写出（配合 BufferedWriter 减少 IO 次数）
    }

    /** CSV 单元格转义：含逗号/引号/换行时双引号包裹并翻倍内部引号（RFC 4180）。 */
    private static String csvCell(String value) {
        boolean needQuote = value.indexOf(',') >= 0       // 含分隔符
                || value.indexOf('"') >= 0                // 含引号
                || value.indexOf('\n') >= 0               // 含换行
                || value.indexOf('\r') >= 0;              // 含回车
        if (needQuote) {
            return '"' + value.replace("\"", "\"\"") + '"'; // 引号包裹 + 内部引号翻倍
        }
        return value; // 普通值原样输出
    }
}
