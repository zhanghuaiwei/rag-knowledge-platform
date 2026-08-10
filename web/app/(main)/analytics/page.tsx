"use client";

import Link from "next/link";
import { Card, Progress, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";

import { api } from "@/api-client";
import type { TopDocument, TokenCost } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { BarChart, LineChart } from "@/components/charts";
import { StatCard } from "@/components/stat-card";
import { formatCost, formatNumber, formatPercent } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

/** mock 配额：真实实现来自租户配额接口（GKB-08，契约待冻结）。 */
const QUOTAS = [
  { label: "存储", used: 186, total: 500, unit: "GB" },
  { label: "文档数", used: 4213, total: 20000, unit: "篇" },
  { label: "本月 Token", used: 3_420_000, total: 10_000_000, unit: "" },
  { label: "查询并发", used: 12, total: 50, unit: "" },
];

const costColumns: TableColumnsType<TokenCost> = [
  { title: "模型", dataIndex: "modelName", render: (v: string) => <Tag color="purple">{v}</Tag> },
  { title: "调用", dataIndex: "calls", render: (v: number) => formatNumber(v) },
  { title: "输入 Token", dataIndex: "tokenIn", render: (v: number) => formatNumber(v) },
  { title: "输出 Token", dataIndex: "tokenOut", render: (v: number) => formatNumber(v) },
  { title: "成本", dataIndex: "cost", render: (v: number) => formatCost(v) },
];

const topColumns: TableColumnsType<TopDocument> = [
  { title: "#", key: "index", render: (_, __, i) => i + 1 },
  {
    title: "文档",
    dataIndex: "fileName",
    render: (v: string, d) => <Link href={`/documents/${d.documentId}`} style={{ color: "var(--primary)" }}>{v}</Link>,
  },
  { title: "知识库", dataIndex: "kbName" },
  { title: "问答引用", dataIndex: "qaCount", render: (v: number) => formatNumber(v) },
  { title: "搜索命中", dataIndex: "searchCount", render: (v: number) => formatNumber(v) },
];

export default function AnalyticsPage() {
  const usage = useAsync(() => api.getDailyUsage());
  const costs = useAsync(() => api.getTokenCosts());
  const topDocs = useAsync(() => api.getTopDocuments());
  const dau = useAsync(() => api.getDau());
  const health = useAsync(() => api.getKnowledgeHealth());

  const totals = (usage.data ?? []).reduce(
    (acc, d) => ({
      qa: acc.qa + d.qaCount,
      search: acc.search + d.searchCount,
      noAnswer: acc.noAnswer + d.noAnswerCount,
      cost: acc.cost + d.cost,
    }),
    { qa: 0, search: 0, noAnswer: 0, cost: 0 },
  );

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">质量与用量</h1>
          <p className="page-desc">分层指标，不用单一综合分掩盖细分场景退化</p>
        </div>
      </div>

      <div className="grid grid-4">
        <StatCard icon="chat" label="14 天问答总量" value={usage.data ? formatNumber(totals.qa) : "…"} extra={usage.data ? `无答案 ${formatNumber(totals.noAnswer)}` : undefined} />
        <StatCard icon="search" label="14 天搜索总量" value={usage.data ? formatNumber(totals.search) : "…"} />
        <StatCard icon="chart" label="14 天成本" value={usage.data ? formatCost(totals.cost) : "…"} extra="含 embedding 与生成" />
        <StatCard
          icon="check"
          label="平均置信度"
          value={health.data ? health.data.averageConfidence.toFixed(2) : "…"}
          extra={health.data ? `低置信率 ${formatPercent(health.data.lowConfRate)}` : undefined}
        />
      </div>

      <div className="grid grid-2">
        <Card title="问答量与无答案量">
          {usage.loading ? (
            <SkeletonRows rows={3} />
          ) : usage.error ? (
            <ErrorState message={usage.error} onRetry={usage.reload} />
          ) : (
            <>
              <LineChart points={(usage.data ?? []).map((d) => ({ label: d.date.slice(5), value: d.qaCount }))} />
              <LineChart points={(usage.data ?? []).map((d) => ({ label: d.date.slice(5), value: d.noAnswerCount }))} height={110} />
            </>
          )}
        </Card>
        <Card title="日活跃用户（DAU）">
          {dau.loading ? (
            <SkeletonRows rows={3} />
          ) : dau.error ? (
            <ErrorState message={dau.error} onRetry={dau.reload} />
          ) : (
            <BarChart points={(dau.data ?? []).map((d) => ({ label: d.date.slice(5), value: d.activeUsers }))} />
          )}
        </Card>
      </div>

      <div className="grid grid-2">
        <Card title="按模型的 Token 与成本">
          <Table<TokenCost>
            rowKey="modelName"
            columns={costColumns}
            dataSource={costs.data ?? []}
            loading={costs.loading}
            pagination={false}
            size="small"
          />
        </Card>

        <Card title="租户配额">
          {QUOTAS.map((q) => {
            const ratio = q.used / q.total;
            return (
              <div key={q.label} style={{ marginBottom: 16 }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13, marginBottom: 6 }}>
                  <span>{q.label}</span>
                  <span style={{ color: "var(--text-2)" }}>
                    {q.unit === "" ? formatNumber(q.used) : `${q.used} ${q.unit}`} / {q.unit === "" ? formatNumber(q.total) : `${q.total} ${q.unit}`}
                  </span>
                </div>
                <Progress
                  percent={Math.round(ratio * 100)}
                  status={ratio > 0.9 ? "exception" : undefined}
                  strokeColor={ratio > 0.7 && ratio <= 0.9 ? "var(--warning)" : undefined}
                />
              </div>
            );
          })}
          <p style={{ fontSize: 12, color: "var(--text-3)" }}>容量超限行为可预测：接近上限时通知所有者与管理员（GKB-08）。</p>
        </Card>
      </div>

      <Card title="热门文档">
        {topDocs.loading ? (
          <SkeletonRows rows={4} />
        ) : (topDocs.data?.length ?? 0) === 0 ? (
          <Empty icon="📊" title="暂无数据" />
        ) : (
          <Table<TopDocument>
            rowKey="documentId"
            columns={topColumns}
            dataSource={topDocs.data ?? []}
            pagination={false}
            size="middle"
          />
        )}
      </Card>
    </div>
  );
}
