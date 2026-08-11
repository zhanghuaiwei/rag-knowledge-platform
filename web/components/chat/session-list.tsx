"use client";

import { Button, List } from "antd";
import { PlusOutlined } from "@ant-design/icons";

import type { ChatSession } from "@/api-client";
import { Loading } from "@/components/async-state";
import { formatRelative } from "@/lib/format";

/** 会话列表：侧栏与移动端抽屉共用（antd List）。 */
export function SessionList({
  sessions,
  loading,
  activeId,
  onSelect,
  onNew,
}: {
  sessions: ChatSession[];
  loading: boolean;
  activeId: number | null;
  onSelect: (id: number) => void;
  onNew: () => void;
}) {
  const showDraft = activeId !== null && !sessions.some((s) => s.id === activeId);
  const items: ChatSession[] = showDraft
    ? [{ id: activeId, title: "新会话", status: "ACTIVE", kbIds: [], messageCount: 0, createdAt: "", updatedAt: new Date().toISOString() }, ...sessions]
    : sessions;

  return (
    <>
      <Button type="primary" block icon={<PlusOutlined />} style={{ marginBottom: 10 }} onClick={onNew}>
        新建会话
      </Button>
      {loading ? (
        <Loading />
      ) : (
        <List
          size="small"
          dataSource={items}
          renderItem={(s) => (
            <List.Item
              style={{ cursor: "pointer", background: s.id === activeId ? "var(--primary-soft)" : undefined, borderRadius: 8, padding: "4px 8px" }}
              onClick={() => onSelect(s.id)}
            >
              <List.Item.Meta
                title={<span style={{ fontWeight: s.id === activeId ? 600 : 400 }}>{s.title}</span>}
                description={`${s.messageCount} 条 · ${formatRelative(s.updatedAt)}`}
              />
            </List.Item>
          )}
        />
      )}
    </>
  );
}
