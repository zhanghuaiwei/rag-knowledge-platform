"use client";

import { Card, Statistic } from "antd";
import type { ReactNode } from "react";

import { Icon, type IconName } from "@/components/icons";

interface StatCardProps {
  icon: IconName;
  label: string;
  value?: string | number;
  extra?: ReactNode;
  loading?: boolean;
  danger?: boolean;
}

/** 统计卡片：基于 antd Statistic，保留图标 + 标签 + 数值 + 辅助信息语义。 */
export function StatCard({ icon, label, value, extra, loading, danger }: StatCardProps) {
  return (
    <Card>
      <Statistic
        title={label}
        value={loading ? "…" : value ?? "—"}
        prefix={<Icon name={icon} size={15} />}
        valueStyle={danger ? { color: "var(--danger)" } : undefined}
      />
      {extra ? <div style={{ fontSize: 12, color: "var(--text-3)", marginTop: 6 }}>{extra}</div> : null}
    </Card>
  );
}
