"use client";

import { useEffect, useState } from "react";
import { Badge, Button, Drawer, List, Tag, Tooltip } from "antd";
import { BellOutlined, CheckOutlined } from "@ant-design/icons";
import { useRouter } from "next/navigation";

import { api } from "@/api-client";
import type { NotificationItem, NotificationLevel } from "@/api-client";
import { Empty } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatRelative } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const LEVEL_COLOR: Record<NotificationLevel, string> = {
  info: "blue",
  success: "success",
  warning: "warning",
  error: "error",
};

const LEVEL_LABEL: Record<NotificationLevel, string> = {
  info: "通知",
  success: "完成",
  warning: "告警",
  error: "失败",
};

/** 通知铃铛 + 抽屉:展示任务完成/失败/审核待办/配额告警/系统通知。 */
export function NotificationCenter() {
  const router = useRouter();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const list = useAsync(() => api.listNotifications(), []);

  // 首次加载与每次打开抽屉时刷新
  const [reloadTick, setReloadTick] = useState(0);
  useEffect(() => {
    if (open) setReloadTick((t) => t + 1);
  }, [open]);
  useEffect(() => {
    if (reloadTick > 0) list.reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadTick]);

  const unread = (list.data ?? []).filter((n) => !n.read).length;

  const handleClick = async (item: NotificationItem) => {
    if (!item.read) {
      try {
        await api.markNotificationRead(item.id);
        list.reload();
      } catch {
        // 标记失败不阻塞跳转
      }
    }
    setOpen(false);
    if (item.href) router.push(item.href);
  };

  const markAll = async () => {
    try {
      await api.markAllNotificationsRead();
      list.reload();
      toast("success", "已全部标记为已读");
    } catch {
      toast("error", "操作失败,请重试");
    }
  };

  return (
    <>
      <Tooltip title="通知">
        <Badge count={unread} size="small" offset={[-2, 2]}>
          <Button
            type="text"
            icon={<BellOutlined />}
            aria-label="通知中心"
            onClick={() => setOpen(true)}
          />
        </Badge>
      </Tooltip>
      <Drawer
        title="通知中心"
        placement="right"
        width={380}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          unread > 0 ? (
            <Button type="link" size="small" icon={<CheckOutlined />} onClick={markAll}>
              全部已读
            </Button>
          ) : undefined
        }
      >
        {list.loading ? (
          <List
            loading
            dataSource={[]}
            renderItem={() => <List.Item />}
          />
        ) : (list.data ?? []).length === 0 ? (
          <Empty icon="🔔" title="暂无通知" desc="任务完成、审核待办和配额告警会出现在这里" />
        ) : (
          <List
            dataSource={list.data ?? []}
            renderItem={(item) => (
              <List.Item
                onClick={() => void handleClick(item)}
                style={{ cursor: item.href ? "pointer" : "default", padding: "12px 0" }}
              >
                <List.Item.Meta
                  avatar={<Tag color={LEVEL_COLOR[item.level]}>{LEVEL_LABEL[item.level]}</Tag>}
                  title={
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                      {!item.read && <Badge status="processing" />}
                      <span style={{ fontWeight: item.read ? 400 : 600 }}>{item.title}</span>
                    </div>
                  }
                  description={
                    <div>
                      <p style={{ margin: 0, color: "var(--text-2)", fontSize: 13 }}>{item.body}</p>
                      <span style={{ fontSize: 12, color: "var(--text-3)" }}>{formatRelative(item.createdAt)}</span>
                    </div>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </Drawer>
    </>
  );
}
