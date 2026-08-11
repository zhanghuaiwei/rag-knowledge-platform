"use client";

import { Button, Tag } from "antd";
import { MenuOutlined } from "@ant-design/icons";

/** 问答页头部：会话标题 + 知识库范围选择入口 + 权限提示。 */
export function ChatHeader({
  title,
  scopeLabel,
  onOpenSessions,
  onOpenScope,
}: {
  title: string;
  scopeLabel: string;
  onOpenSessions: () => void;
  onOpenScope: () => void;
}) {
  return (
    <div
      style={{
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 10,
        flexWrap: "wrap",
        paddingBottom: 12,
        borderBottom: "1px solid var(--border)",
        marginBottom: 12,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 8, minWidth: 0 }}>
        <Button type="text" className="chat-sessions-toggle" icon={<MenuOutlined />} aria-label="会话列表" onClick={onOpenSessions} />
        <div style={{ minWidth: 0 }}>
          <strong>{title}</strong>
          <Button
            type="text"
            size="small"
            style={{ color: "var(--text-3)", fontSize: 12, marginLeft: 4, padding: "0 4px", height: "auto" }}
            onClick={onOpenScope}
          >
            范围：{scopeLabel}
          </Button>
        </div>
      </div>
      <Tag color="processing">回答仅基于你有权限的内容</Tag>
    </div>
  );
}
