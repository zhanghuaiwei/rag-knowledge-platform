"use client";

import { useState } from "react";
import { Button, Card, Switch, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { Webhook } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

export default function WebhooksPage() {
  const toast = useToast();
  const webhooks = useAsync(() => api.listWebhooks());
  const [paused, setPaused] = useState<Set<number>>(new Set());
  const [pendingId, setPendingId] = useState<number | null>(null);

  const toggle = (id: number, currentlyPaused: boolean) => {
    setPendingId(id);
    setTimeout(() => {
      setPaused((prev) => {
        const next = new Set(prev);
        if (currentlyPaused) next.delete(id);
        else next.add(id);
        return next;
      });
      setPendingId(null);
      toast("success", currentlyPaused ? "Webhook 已恢复投递（mock）" : "Webhook 已暂停（mock）");
    }, 400);
  };

  const columns: TableColumnsType<Webhook> = [
    { title: "名称", dataIndex: "name", render: (v: string) => <strong>{v}</strong> },
    { title: "目标地址", dataIndex: "targetUrl", render: (v: string) => <Typography.Text code>{v}</Typography.Text> },
    {
      title: "事件类型",
      dataIndex: "eventTypes",
      render: (types: string[]) => (
        <>
          {types.map((t) => <Tag key={t} color="processing">{t}</Tag>)}
        </>
      ),
    },
    { title: "创建时间", dataIndex: "createdAt", render: (v: string) => formatDateTime(v).slice(0, 10) },
    {
      title: "投递状态",
      key: "status",
      render: (_, hook) => {
        const isPaused = paused.has(hook.id) || hook.status === "PAUSED";
        return (
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <Switch
              checked={!isPaused}
              loading={pendingId === hook.id}
              onChange={() => toggle(hook.id, isPaused)}
              checkedChildren="投递"
              unCheckedChildren="暂停"
            />
            <Tag color={isPaused ? "warning" : "success"}>{isPaused ? "已暂停" : "投递中"}</Tag>
          </div>
        );
      },
    },
  ];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · Webhook</h1>
          <p className="page-desc">签名投递、重试退避与死信队列；禁止内网与元数据地址</p>
        </div>
        <div className="page-actions">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => toast("info", "mock：创建 Webhook（egress allowlist 校验，契约待冻结）")}>
            创建 Webhook
          </Button>
        </div>
      </div>

      {webhooks.loading ? (
        <Card>
          <SkeletonRows rows={3} />
        </Card>
      ) : webhooks.error ? (
        <Card>
          <ErrorState message={webhooks.error} onRetry={webhooks.reload} />
        </Card>
      ) : (
        <Card>
          <Table<Webhook>
            rowKey="id"
            columns={columns}
            dataSource={webhooks.data ?? []}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  icon="🔗"
                  title="暂无 Webhook"
                  desc="订阅文档发布、审核、同步等事件"
                  action={
                    <Button onClick={() => toast("info", "mock：创建 Webhook（契约待冻结）")}>
                      创建 Webhook
                    </Button>
                  }
                />
              ),
            }}
          />
        </Card>
      )}
    </div>
  );
}
