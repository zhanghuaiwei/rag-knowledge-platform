"use client";

import { Button, Empty as AntdEmpty, Result, Skeleton, Spin } from "antd";
import type { ReactNode } from "react";

/** 加载骨架：antd Skeleton.Node 占位，保持自定义高度/行数语义。 */
export function SkeletonRows({ rows = 4, height = 44 }: { rows?: number; height?: number }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton.Node key={i} active style={{ width: "100%", height }} />
      ))}
    </div>
  );
}

export function Loading({ text = "加载中…" }: { text?: string }) {
  return (
    <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 10, padding: "48px 0" }}>
      <Spin size="small" />
      <span style={{ color: "var(--text-2)" }}>{text}</span>
    </div>
  );
}

export function Empty({ icon, title, desc, action }: { icon?: string; title: string; desc?: string; action?: ReactNode }) {
  return (
    <AntdEmpty
      image={AntdEmpty.PRESENTED_IMAGE_SIMPLE}
      description={
        <div>
          {icon ? <div style={{ fontSize: 30, lineHeight: 1.4 }}>{icon}</div> : null}
          <div style={{ fontWeight: 600, marginTop: 4 }}>{title}</div>
          {desc ? <p style={{ color: "var(--text-2)", fontSize: 13, margin: "4px 0 0" }}>{desc}</p> : null}
        </div>
      }
      style={{ margin: "20px 0" }}
    >
      {action}
    </AntdEmpty>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <Result
      status="error"
      title="加载失败"
      subTitle={message}
      extra={onRetry ? <Button type="primary" onClick={onRetry}>重试</Button> : undefined}
    />
  );
}
