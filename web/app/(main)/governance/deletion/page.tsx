"use client";

import { Button, Card, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { CheckOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { DeletionReceipt, DeletionTask, DeletionTaskStatus } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const STATUS_LABEL: Record<DeletionTaskStatus, [string, string]> = {
  PENDING_APPROVAL: ["待审批", "warning"],
  RUNNING: ["处置中", "processing"],
  SUCCEEDED: ["已完成", "success"],
  FAILED: ["失败", "error"],
};

export default function DeletionPage() {
  const toast = useToast();
  const tasks = useAsync(() => api.listDeletionTasks());
  const receipts = useAsync(() => api.listDeletionReceipts());

  const approve = async (id: number) => {
    try {
      await api.approveDeletion(id);
      toast("success", "删除已批准，正在传播到各副本");
      tasks.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "批准失败");
    }
  };

  const taskColumns: TableColumnsType<DeletionTask> = [
    { title: "文档", dataIndex: "fileName", render: (v: string) => <strong>{v}</strong> },
    { title: "原因", dataIndex: "reason", ellipsis: true },
    { title: "申请人", dataIndex: "requestedBy", width: 90 },
    {
      title: "状态",
      dataIndex: "status",
      width: 100,
      render: (v: DeletionTaskStatus) => {
        const [label, color] = STATUS_LABEL[v];
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: "副本处置",
      key: "progress",
      render: (_, t) => (
        <span style={{ fontSize: 12, color: "var(--text-2)" }}>
          {["对象存储", "索引", "缓存", "备份"].map((name, i) => {
            const done = Object.values(t.progress)[i];
            return (
              <Tag key={name} color={done ? "success" : "default"} style={{ marginInlineEnd: 4 }}>
                {done ? `${name} ✓` : name}
              </Tag>
            );
          })}
        </span>
      ),
    },
    { title: "提交时间", dataIndex: "createdAt", width: 130, render: (v: string) => formatDateTime(v) },
    {
      title: "操作",
      key: "action",
      width: 90,
      render: (_, t) =>
        t.status === "PENDING_APPROVAL" ? (
          <Button size="small" type="primary" icon={<CheckOutlined />} onClick={() => void approve(t.id)}>
            批准
          </Button>
        ) : (
          "—"
        ),
    },
  ];

  const receiptColumns: TableColumnsType<DeletionReceipt> = [
    { title: "文档", dataIndex: "fileName", render: (v: string) => <strong>{v}</strong> },
    { title: "校验值", dataIndex: "checksum", render: (v: string) => <code>{v}</code> },
    { title: "操作人", dataIndex: "operator", width: 90 },
    { title: "删除时间", dataIndex: "deletedAt", width: 130, render: (v: string) => formatDateTime(v) },
  ];

  return (
    <>
      <Card title="删除审批" style={{ marginBottom: 16 }}>
        {tasks.loading ? (
          <SkeletonRows rows={3} />
        ) : tasks.error ? (
          <ErrorState message={tasks.error} onRetry={tasks.reload} />
        ) : (tasks.data?.length ?? 0) === 0 ? (
          <Empty icon="🗑️" title="暂无删除任务" />
        ) : (
          <Table<DeletionTask> rowKey="id" columns={taskColumns} dataSource={tasks.data ?? []} pagination={false} size="small" />
        )}
        <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 12 }}>
          删除为逻辑删除：批准后依次处置对象存储/索引/缓存/备份副本，全部完成才生成可校验的删除证明。
        </p>
      </Card>

      <Card title="删除证明">
        {receipts.loading ? (
          <SkeletonRows rows={2} />
        ) : receipts.error ? (
          <ErrorState message={receipts.error} onRetry={receipts.reload} />
        ) : (receipts.data?.length ?? 0) === 0 ? (
          <Empty icon="🧾" title="暂无删除证明" />
        ) : (
          <Table<DeletionReceipt> rowKey="id" columns={receiptColumns} dataSource={receipts.data ?? []} pagination={false} size="small" />
        )}
      </Card>
    </>
  );
}
