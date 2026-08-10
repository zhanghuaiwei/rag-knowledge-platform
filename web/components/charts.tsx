"use client";

import { useMemo } from "react";

import { EChart, cssVar, type EChartsCoreOption } from "@/components/echarts";
import { useTheme } from "@/components/theme-provider";

export interface ChartPoint {
  label: string;
  value: number;
}

function useThemeColors() {
  const { config } = useTheme();
  // config 变化时重新取色，保证图表随主题联动
  return useMemo(
    () => ({
      primary: cssVar("--primary", "#2f6bff"),
      text3: cssVar("--text-3", "#8b96ad"),
      border: cssVar("--border", "#e4e8f0"),
    }),
    // config 整体变化即重取色，无需逐项展开
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [config],
  );
}

export function LineChart({ points, height = 180 }: { points: ChartPoint[]; height?: number }) {
  const colors = useThemeColors();
  const option = useMemo<EChartsCoreOption>(
    () => ({
      animationDuration: 600,
      grid: { left: 40, right: 12, top: 16, bottom: 28 },
      tooltip: { trigger: "axis" },
      xAxis: {
        type: "category",
        data: points.map((p) => p.label),
        axisLine: { lineStyle: { color: colors.border } },
        axisLabel: { color: colors.text3, fontSize: 10 },
        axisTick: { show: false },
      },
      yAxis: {
        type: "value",
        splitLine: { lineStyle: { color: colors.border, type: "dashed" } },
        axisLabel: { color: colors.text3, fontSize: 10 },
      },
      series: [
        {
          type: "line",
          data: points.map((p) => p.value),
          smooth: true,
          symbolSize: 5,
          lineStyle: { width: 2, color: colors.primary },
          itemStyle: { color: colors.primary },
          areaStyle: { color: colors.primary, opacity: 0.12 },
        },
      ],
    }),
    [points, colors],
  );
  return <EChart option={option} height={height} />;
}

export function BarChart({
  points,
  height = 180,
  color,
}: {
  points: ChartPoint[];
  height?: number;
  color?: string;
}) {
  const colors = useThemeColors();
  const barColor = color ?? colors.primary;
  const option = useMemo<EChartsCoreOption>(
    () => ({
      animationDuration: 600,
      grid: { left: 40, right: 12, top: 16, bottom: 28 },
      tooltip: { trigger: "axis" },
      xAxis: {
        type: "category",
        data: points.map((p) => p.label),
        axisLine: { lineStyle: { color: colors.border } },
        axisLabel: { color: colors.text3, fontSize: 10, interval: 0, rotate: points.length > 8 ? 30 : 0 },
        axisTick: { show: false },
      },
      yAxis: {
        type: "value",
        splitLine: { lineStyle: { color: colors.border, type: "dashed" } },
        axisLabel: { color: colors.text3, fontSize: 10 },
      },
      series: [
        {
          type: "bar",
          data: points.map((p) => p.value),
          barMaxWidth: 36,
          itemStyle: { color: barColor, borderRadius: [6, 6, 0, 0], opacity: 0.88 },
        },
      ],
    }),
    [points, barColor, colors],
  );
  return <EChart option={option} height={height} />;
}

export function Donut({ ratio, size = 96, label }: { ratio: number; size?: number; label?: string }) {
  const colors = useThemeColors();
  const clamped = Math.min(Math.max(ratio, 0), 1);
  const option = useMemo<EChartsCoreOption>(
    () => ({
      animationDuration: 600,
      title: {
        text: `${Math.round(clamped * 100)}%`,
        left: "center",
        top: "center",
        textStyle: { fontSize: size / 4.6, color: colors.primary, fontWeight: 700 },
        subtext: label ?? "",
        subtextStyle: { fontSize: 10, color: colors.text3 },
      },
      series: [
        {
          type: "pie",
          radius: ["72%", "88%"],
          avoidLabelOverlap: false,
          label: { show: false },
          data: [
            { value: clamped, itemStyle: { color: colors.primary, borderRadius: 6 } },
            { value: 1 - clamped, itemStyle: { color: colors.border } },
          ],
        },
      ],
    }),
    [clamped, size, label, colors],
  );
  return <EChart option={option} height={size} />;
}
