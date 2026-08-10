"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";

import { api } from "@/api-client";
import { EChart, type EChartsCoreOption } from "@/components/echarts";
import { Icon } from "@/components/icons";
import { formatNumber } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const AXIS_COLOR = "#5f7396";
const SPLIT_COLOR = "rgba(96, 140, 255, 0.16)";
const PALETTE = ["#5f8dff", "#9f7bff", "#3dd6a7", "#ffb84d", "#ff6b6b", "#4dc3ff"];

const axisBase = {
  axisLine: { lineStyle: { color: SPLIT_COLOR } },
  axisLabel: { color: AXIS_COLOR, fontSize: 10 },
  axisTick: { show: false },
} as const;

/**
 * 数据大屏（mock 演示）：全屏暗色运营视图。
 * 指标口径与 analytics 页一致；真实环境由租户级指标接口驱动（GKB-08，契约待冻结）。
 */
export default function ScreenPage() {
  const [now, setNow] = useState(() => new Date());
  const usage = useAsync(() => api.getDailyUsage());
  const costs = useAsync(() => api.getTokenCosts());
  const topDocs = useAsync(() => api.getTopDocuments());
  const dau = useAsync(() => api.getDau());
  const health = useAsync(() => api.getKnowledgeHealth());
  const audit = useAsync(() => api.listAuditLogs({ page: 1, size: 8 }));
  const docs = useAsync(() => api.listDocuments({ page: 1, size: 50 }));

  // 时钟 + 每 30s 刷新数据
  useEffect(() => {
    const clock = setInterval(() => setNow(new Date()), 1000);
    const refresh = setInterval(() => {
      usage.reload();
      health.reload();
      audit.reload();
    }, 30000);
    return () => {
      clearInterval(clock);
      clearInterval(refresh);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const latest = usage.data?.at(-1);
  const answerRate = health.data ? 1 - health.data.noAnswerRate : 0;

  const trendOption = useMemo<EChartsCoreOption>(() => {
    const days = usage.data ?? [];
    return {
      animationDuration: 800,
      color: [PALETTE[0], PALETTE[2]],
      legend: { data: ["问答", "搜索"], textStyle: { color: AXIS_COLOR }, top: 0 },
      grid: { left: 44, right: 14, top: 32, bottom: 26 },
      tooltip: { trigger: "axis" },
      xAxis: { type: "category", data: days.map((d) => d.date.slice(5)), ...axisBase },
      yAxis: { type: "value", ...axisBase, splitLine: { lineStyle: { color: SPLIT_COLOR, type: "dashed" } } },
      series: [
        { name: "问答", type: "line", smooth: true, data: days.map((d) => d.qaCount), areaStyle: { opacity: 0.18 }, symbolSize: 4 },
        { name: "搜索", type: "line", smooth: true, data: days.map((d) => d.searchCount), areaStyle: { opacity: 0.12 }, symbolSize: 4 },
      ],
    };
  }, [usage.data]);

  const costOption = useMemo<EChartsCoreOption>(
    () => ({
      animationDuration: 800,
      color: PALETTE,
      tooltip: { trigger: "item", valueFormatter: (v: number) => `¥${Number(v).toFixed(2)}` },
      legend: { bottom: 0, textStyle: { color: AXIS_COLOR, fontSize: 10 }, itemWidth: 10, itemHeight: 10 },
      series: [
        {
          type: "pie",
          radius: ["42%", "66%"],
          center: ["50%", "44%"],
          label: { color: AXIS_COLOR, fontSize: 10 },
          data: (costs.data ?? []).map((c) => ({ name: c.modelName, value: Number(c.cost.toFixed(2)) })),
        },
      ],
    }),
    [costs.data],
  );

  const dauOption = useMemo<EChartsCoreOption>(
    () => ({
      animationDuration: 800,
      grid: { left: 40, right: 12, top: 18, bottom: 26 },
      tooltip: { trigger: "axis" },
      xAxis: { type: "category", data: (dau.data ?? []).map((d) => d.date.slice(5)), ...axisBase },
      yAxis: { type: "value", ...axisBase, splitLine: { lineStyle: { color: SPLIT_COLOR, type: "dashed" } } },
      series: [
        {
          type: "bar",
          data: (dau.data ?? []).map((d) => d.activeUsers),
          barMaxWidth: 22,
          itemStyle: {
            borderRadius: [5, 5, 0, 0],
            color: {
              type: "linear", x: 0, y: 0, x2: 0, y2: 1,
              colorStops: [{ offset: 0, color: "#7fb0ff" }, { offset: 1, color: "rgba(95,141,255,0.25)" }],
            },
          },
        },
      ],
    }),
    [dau.data],
  );

  const gaugeOption = useMemo<EChartsCoreOption>(
    () => ({
      series: [
        {
          type: "gauge",
          startAngle: 210,
          endAngle: -30,
          min: 0,
          max: 100,
          progress: { show: true, width: 10, itemStyle: { color: "#3dd6a7" } },
          axisLine: { lineStyle: { width: 10, color: [[1, "rgba(96,140,255,0.18)"]] } },
          axisTick: { show: false },
          splitLine: { show: false },
          axisLabel: { show: false },
          pointer: { show: false },
          anchor: { show: false },
          title: { color: AXIS_COLOR, fontSize: 11, offsetCenter: [0, "32%"] },
          detail: { valueAnimation: true, fontSize: 26, color: "#dce6f7", offsetCenter: [0, "-4%"], formatter: "{value}%" },
          data: [{ value: Number((answerRate * 100).toFixed(1)), name: "回答率" }],
        },
      ],
    }),
    [answerRate],
  );

  const ingestOption = useMemo<EChartsCoreOption>(() => {
    const counts = new Map<string, number>();
    (docs.data?.items ?? []).forEach((d) => counts.set(d.ingestStatus, (counts.get(d.ingestStatus) ?? 0) + 1));
    const labels = [...counts.keys()];
    return {
      animationDuration: 800,
      grid: { left: 70, right: 20, top: 10, bottom: 24 },
      tooltip: { trigger: "axis" },
      xAxis: { type: "value", ...axisBase, splitLine: { lineStyle: { color: SPLIT_COLOR, type: "dashed" } } },
      yAxis: { type: "category", data: labels, ...axisBase },
      series: [
        { type: "bar", data: labels.map((l) => counts.get(l)), barMaxWidth: 14, itemStyle: { color: PALETTE[5], borderRadius: 4 } },
      ],
    };
  }, [docs.data]);

  const topDocsOption = useMemo<EChartsCoreOption>(
    () => ({
      animationDuration: 800,
      grid: { left: 8, right: 30, top: 10, bottom: 24, containLabel: true },
      tooltip: { trigger: "axis" },
      xAxis: { type: "value", ...axisBase, splitLine: { lineStyle: { color: SPLIT_COLOR, type: "dashed" } } },
      yAxis: {
        type: "category",
        inverse: true,
        data: (topDocs.data ?? []).slice(0, 6).map((d) => (d.fileName.length > 14 ? `${d.fileName.slice(0, 14)}…` : d.fileName)),
        ...axisBase,
      },
      series: [
        { type: "bar", data: (topDocs.data ?? []).slice(0, 6).map((d) => d.qaCount), barMaxWidth: 14, itemStyle: { color: PALETTE[1], borderRadius: 4 } },
      ],
    }),
    [topDocs.data],
  );

  const tickerItems = [...(audit.data?.items ?? []), ...(audit.data?.items ?? [])];

  return (
    <div className="screen-page">
      <div className="screen-header">
        <Link href="/dashboard" className="btn btn-sm" style={{ background: "rgba(47,107,255,0.15)", borderColor: "rgba(96,140,255,0.35)", color: "#a8c0f0" }}>
          <Icon name="arrow-left" size={13} /> 退出大屏
        </Link>
        <div className="screen-title">企业知识库运营数据大屏</div>
        <span className="screen-clock">
          {now.toLocaleDateString("zh-CN")} {now.toLocaleTimeString("zh-CN", { hour12: false })}
        </span>
      </div>

      <div className="screen-kpis">
        {[
          { label: "今日问答", value: latest ? formatNumber(latest.qaCount) : "…" },
          { label: "今日搜索", value: latest ? formatNumber(latest.searchCount) : "…" },
          { label: "活跃用户", value: latest ? formatNumber(latest.activeUsers) : "…" },
          { label: "今日成本", value: latest ? `¥${latest.cost.toFixed(2)}` : "…" },
        ].map((kpi) => (
          <div key={kpi.label} className="screen-panel screen-kpi">
            <div className="screen-kpi-value">{kpi.value}</div>
            <div className="screen-kpi-label">{kpi.label}（mock）</div>
          </div>
        ))}
      </div>

      <div className="screen-grid">
        <div className="screen-panel">
          <div className="screen-panel-title">回答质量</div>
          <EChart option={gaugeOption} height={190} />
        </div>
        <div className="screen-panel wide">
          <div className="screen-panel-title">近 14 天问答 / 搜索趋势</div>
          <EChart option={trendOption} height={190} />
        </div>

        <div className="screen-panel">
          <div className="screen-panel-title">文档摄取状态分布</div>
          <EChart option={ingestOption} height={210} />
        </div>
        <div className="screen-panel">
          <div className="screen-panel-title">模型成本占比（14 天）</div>
          <EChart option={costOption} height={210} />
        </div>
        <div className="screen-panel">
          <div className="screen-panel-title">日活跃用户</div>
          <EChart option={dauOption} height={210} />
        </div>

        <div className="screen-panel wide">
          <div className="screen-panel-title">热门文档（按问答引用）</div>
          <EChart option={topDocsOption} height={200} />
        </div>
        <div className="screen-panel">
          <div className="screen-panel-title">实时审计动态</div>
          <div className="screen-ticker">
            <div className="screen-ticker-track">
              {tickerItems.map((log, i) => (
                <div key={`${log.id}-${i}`} className="screen-ticker-item">
                  <span className="dot" style={{ color: log.result === "SUCCEEDED" ? "#3dd6a7" : log.result === "DENIED" ? "#ff6b6b" : "#ffb84d" }} />
                  <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                    {log.actor} · {log.action} · {log.resourceType}#{log.resourceId}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <p style={{ textAlign: "center", color: "#4d5f80", fontSize: 12, marginTop: 16 }}>
        数据每 30 秒自动刷新 · 当前为 mock 演示数据，指标口径以 GKB-08 SLO 定义为准
      </p>
    </div>
  );
}
