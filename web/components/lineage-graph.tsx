"use client";

import { Graph } from "@antv/g6";
import { useEffect, useRef } from "react";

export type LineageNodeKind = "source" | "document" | "chunk" | "index" | "consumer";

export interface LineageNode {
  id: string;
  label: string;
  kind: LineageNodeKind;
}

export interface LineageEdge {
  source: string;
  target: string;
  label?: string;
}

/** 节点按血缘阶段着色：来源 → 文档 → 分块 → 索引 → 消费。 */
const KIND_COLOR: Record<LineageNodeKind, string> = {
  source: "#9f7bff",
  document: "#5f8dff",
  chunk: "#3dd6a7",
  index: "#ffb84d",
  consumer: "#ff6b6b",
};

/**
 * 数据血缘可视化（G6 v5）：从 mock 血缘数据渲染力导向图，
 * 支持拖拽画布 / 缩放 / 拖拽节点。真实环境由血缘接口返回节点与边。
 * 注：G6 走 canvas，不能用 CSS 变量配色，文字/背景色按 dark 参数取具体色值。
 */
export function LineageGraph({
  nodes,
  edges,
  height = 340,
  dark = false,
}: {
  nodes: LineageNode[];
  edges: LineageEdge[];
  height?: number;
  dark?: boolean;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const textColor = dark ? "#e6eaf3" : "#16213a";
  const edgeLabelBg = dark ? "#151c2e" : "#ffffff";

  useEffect(() => {
    const el = containerRef.current;
    if (!el || nodes.length === 0) return;

    const graph = new Graph({
      container: el,
      autoResize: true,
      autoFit: "view",
      theme: dark ? "dark" : "light",
      data: {
        nodes: nodes.map((n) => ({
          id: n.id,
          data: { label: n.label, kind: n.kind },
        })),
        edges: edges.map((e) => ({
          source: e.source,
          target: e.target,
          data: { label: e.label ?? "" },
        })),
      },
      node: {
        style: {
          size: 42,
          fill: (d) => KIND_COLOR[(d.data as { kind: LineageNodeKind }).kind] ?? "#5f8dff",
          stroke: "#fff",
          lineWidth: 2,
          labelText: (d) => (d.data as { label: string }).label,
          labelFill: textColor,
          labelPlacement: "bottom",
          labelMaxWidth: 190,
          labelPadding: 6,
        },
      },
      edge: {
        style: {
          stroke: "#8b9bb4",
          lineWidth: 1.2,
          endArrow: true,
          labelText: (d) => (d.data as { label: string }).label,
          labelFill: textColor,
          labelBackground: true,
          labelBackgroundFill: edgeLabelBg,
          labelBackgroundRadius: 3,
          labelPadding: [2, 5, 2, 5],
        },
      },
      layout: { type: "force", preventOverlap: true, linkDistance: 160, nodeStrength: -120 },
      behaviors: ["drag-canvas", "zoom-canvas", "drag-element"],
    });

    void graph.render();
    return () => graph.destroy();
  }, [nodes, edges, dark, textColor, edgeLabelBg]);

  if (nodes.length === 0) return null;
  return (
    <div>
      <div ref={containerRef} style={{ width: "100%", height }} />
      <div style={{ display: "flex", gap: 16, flexWrap: "wrap", marginTop: 8, fontSize: 12, color: "var(--text-3)" }}>
        {(Object.keys(KIND_COLOR) as LineageNodeKind[]).map((kind) => (
          <span key={kind} style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
            <span style={{ width: 12, height: 12, borderRadius: "50%", background: KIND_COLOR[kind], display: "inline-block" }} />
            {kind === "source" ? "来源" : kind === "document" ? "文档" : kind === "chunk" ? "分块" : kind === "index" ? "索引" : "消费"}
          </span>
        ))}
      </div>
    </div>
  );
}
