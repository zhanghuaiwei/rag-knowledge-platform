"use client";

import { useState } from "react";
import { Button, Card, Form, Input, Modal, Select, Switch, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { Webhook } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const EVENT_OPTIONS = ["document.review.updated", "document.ingest.finished", "document.ingest.failed", "index.build.published", "legal_hold.changed"];

interface CreateFormValues {
  name: string;
  targetUrl: string;
  eventTypes: string[];
}

export default function WebhooksPage() {
  const toast = useToast();
  const webhooks = useAsync(() => api.listWebhooks());
  const [createForm] = Form.useForm<CreateFormValues>();
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const toggle = async (id: number, currentlyPaused: boolean) => {
    setPendingId(id);
    try {
      await api.toggleWebhook(id, currentlyPaused);
      toast("success", currentlyPaused ? "Webhook 已恢复投递" : "Webhook 已暂停");
      webhooks.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "操作失败");
    } finally {
      setPendingId(null);
    }
  };

  const handleCreate = async (values: CreateFormValues) => {
    setCreating(true);
    try {
      await api.createWebhook(values);
      toast("success", "Webhook 已创建");
      setCreateOpen(false);
      createForm.resetFields();
      webhooks.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreating(false);
    }
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
        const isPaused = hook.status === "PAUSED";
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
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
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
                    <Button onClick={() => setCreateOpen(true)}>
                      创建 Webhook
                    </Button>
                  }
                />
              ),
            }}
          />
        </Card>
      )}

      <Modal
        title="创建 Webhook"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        okText={creating ? "创建中…" : "创建"}
        confirmLoading={creating}
      >
        <Form<CreateFormValues> form={createForm} layout="vertical" requiredMark={false} onFinish={(v) => void handleCreate(v)}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
            <Input placeholder="例如：合规事件推送" maxLength={40} />
          </Form.Item>
          <Form.Item
            name="targetUrl"
            label="目标地址（HTTPS）"
            rules={[
              { required: true, message: "请输入回调地址" },
              { type: "url", message: "请输入合法的 HTTPS 地址" },
            ]}
            extra="禁止内网与元数据地址；egress allowlist 以服务端复核为准"
          >
            <Input placeholder="https://hooks.example.com/ragkb" />
          </Form.Item>
          <Form.Item name="eventTypes" label="事件类型" rules={[{ required: true, message: "至少订阅一个事件" }]}>
            <Select mode="multiple" placeholder="选择订阅事件" options={EVENT_OPTIONS.map((e) => ({ value: e, label: e }))} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
