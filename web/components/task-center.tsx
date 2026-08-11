"use client";

import { useEffect, useState } from "react";
import { Badge, Button, Drawer, List, Progress, Tag, Tooltip } from "antd";
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  PauseCircleOutlined,
  ThunderboltOutlined,
} from "@ant-design/icons";
import { useRouter } from "next/navigation";

import { api } from "@/api-client";
import type { Task, TaskStatus, TaskType } from "@/api-client";
import { Empty } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatRelative } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const STATUS_ICON: Record<TaskStatus, React.ReactNode> = {
  PENDING: <ClockCircleOutlined style={{ color: "var(--text-3)" }} />,
  RUNNING: <LoadingOutlined style={{ color: "var(--primary)" }} />,
  SUCCEEDED: <CheckCircleOutlined style={{ color: "var(--success)" }} />,
  FAILED: <CloseCircleOutlined style={{ color: "var(--error)" }} />,
  CANCELLING: <PauseCircleOutlined style={{ color: "var(--warning)" }} />,
  CANCELLED: <PauseCircleOutlined style={{ color: "var(--text-3)" }} />,
};

const TYPE_LABEL: Record<TaskType, string> = {
  UPLOAD: "上传",
  INGEST: "摄取",
  INDEX_BUILD: "索引重建",
  SYNC: "同步",
  DELETE: "删除",
  EXPORT: "导出",
};

const TYPE_COLOR: Record<TaskType, string> = {
  UPLOAD: "blue",
  INGEST: "processing",
  INDEX_BUILD: "purple",
  SYNC: "cyan",
  DELETE: "error",
  EXPORT: "gold",
};

/**
 * 任务中心抽屉:展示当前用户的异步任务(上传/摄取/索引重建/同步/删除/导出)。
 * 运行中任务轮询刷新,可取消(mock 提示)。
 */
export function TaskCenter() {
  const router = useRouter();
  const toast = useToast();
  const [open, setOpen] = useState(false);
  const tasks = useAsync(() => api.listTasks(), []);

  // 运行中任务每 3s 轮询
  useEffect(() => {
    if (!open) return;
    const hasRunning = (tasks.data ?? []).some((t) => t.status === "RUNNING" || t.status === "PENDING");
    if (!hasRunning) return;
    const timer = setInterval(() => tasks.reload(), 3000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, tasks.data]);

  const running = (tasks.data ?? []).filter((t) => t.status === "RUNNING" || t.status === "PENDING").length;

  const cancel = async (task: Task) => {
    try {
      await api.cancelTask(task.id);
      toast("success", `已取消任务「${task.title}」`);
      tasks.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "取消失败");
    }
  };

  const openResource = (task: Task) => {
    setOpen(false);
    if (task.resourceType === "DOCUMENT" && task.resourceId) {
      router.push(`/documents/${task.resourceId}`);
    } else if (task.resourceType === "KB" && task.resourceId) {
      router.push(`/kbs/${task.resourceId}`);
    } else if (task.resourceType === "AUDIT") {
      router.push("/admin/audit");
    }
  };

  return (
    <>
      <Tooltip title="任务中心">
        <Badge count={running} size="small" offset={[-2, 2]}>
          <Button
            type="text"
            icon={<ThunderboltOutlined />}
            aria-label="任务中心"
            onClick={() => setOpen(true)}
          />
        </Badge>
      </Tooltip>
      <Drawer
        title="任务中心"
        placement="right"
        width={420}
        open={open}
        onClose={() => setOpen(false)}
      >
        {tasks.loading && !tasks.data ? (
          <List loading dataSource={[]} renderItem={() => <List.Item />} />
        ) : (tasks.data ?? []).length === 0 ? (
          <Empty icon="📋" title="暂无任务" desc="上传、索引重建、同步等异步任务会出现在这里" />
        ) : (
          <List
            dataSource={tasks.data ?? []}
            renderItem={(task) => (
              <List.Item style={{ padding: "12px 0", alignItems: "flex-start" }}>
                <div style={{ width: "100%" }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
                    {STATUS_ICON[task.status]}
                    <Tag color={TYPE_COLOR[task.type]}>{TYPE_LABEL[task.type]}</Tag>
                    <span style={{ fontWeight: 500, flex: 1, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                      {task.title}
                    </span>
                  </div>
                  {(task.status === "RUNNING" || task.status === "PENDING") ? (
                    <Progress percent={task.progress} size="small" status={task.status === "PENDING" ? "active" : "normal"} />
                  ) : null}
                  {task.message ? (
                    <p style={{ margin: "4px 0 0", fontSize: 12, color: "var(--text-3)" }}>{task.message}</p>
                  ) : null}
                  <div style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 4 }}>
                    <span style={{ fontSize: 12, color: "var(--text-3)" }}>
                      {formatRelative(task.startedAt)}
                    </span>
                    <div style={{ flex: 1 }} />
                    {(task.status === "RUNNING" || task.status === "PENDING") ? (
                      <Button type="link" size="small" danger onClick={() => cancel(task)}>
                        取消
                      </Button>
                    ) : null}
                    {task.resourceType ? (
                      <Button type="link" size="small" onClick={() => openResource(task)}>
                        查看
                      </Button>
                    ) : null}
                  </div>
                </div>
              </List.Item>
            )}
          />
        )}
      </Drawer>
    </>
  );
}
