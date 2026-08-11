"use client";

import { useState } from "react";
import { Button, Card, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { CopyOutlined, PlusOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { ApiKey } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatDateTime, formatRelative } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const SCOPE_OPTIONS = ["search:read", "chat:write", "docs:read", "docs:write", "analytics:read"];

interface CreateFormValues {
  name: string;
  scopes: string[];
  kbId?: number;
  expireDays: number;
}

export default function ApiKeysPage() {
  const toast = useToast();
  const keys = useAsync(() => api.listApiKeys());
  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));
  const [createForm] = Form.useForm<CreateFormValues>();

  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<ApiKey | null>(null);
  const [revoking, setRevoking] = useState(false);

  const handleCreate = async (values: CreateFormValues) => {
    setCreating(true);
    try {
      const { secret } = await api.createApiKey({
        name: values.name,
        scopes: values.scopes,
        kbId: values.kbId,
        expireDays: values.expireDays,
      });
      setNewSecret(secret);
      setCreateOpen(false);
      createForm.resetFields();
      keys.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const copySecret = async () => {
    if (!newSecret) return;
    try {
      await navigator.clipboard.writeText(newSecret);
      toast("success", "已复制到剪贴板");
    } catch {
      toast("info", "请手动选择复制");
    }
  };

  const confirmRevoke = async () => {
    if (!revokeTarget) return;
    setRevoking(true);
    try {
      await api.revokeApiKey(revokeTarget.id);
      toast("success", `「${revokeTarget.name}」已吊销，立即生效`);
      setRevokeTarget(null);
      keys.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "吊销失败");
    } finally {
      setRevoking(false);
    }
  };

  const allKeys = keys.data ?? [];

  const columns: TableColumnsType<ApiKey> = [
    { title: "名称", dataIndex: "name", render: (name: string) => <strong>{name}</strong> },
    { title: "前缀", dataIndex: "keyPrefix", render: (p: string) => <Typography.Text code>{p}…</Typography.Text> },
    {
      title: "Scope",
      dataIndex: "scopes",
      render: (scopes: string[]) => (
        <Space size={4} wrap>
          {scopes.map((s) => <Tag key={s} color="processing">{s}</Tag>)}
        </Space>
      ),
    },
    {
      title: "知识库范围",
      dataIndex: "kbIds",
      render: (kbIds: number[]) => (kbIds.length ? `${kbIds.length} 个库` : "按授权"),
    },
    {
      title: "状态",
      key: "status",
      render: (_, key) => {
        const label = key.status === "REVOKED" ? "已吊销" : key.status === "EXPIRED" ? "已过期" : "有效";
        const color = key.status === "REVOKED" ? "error" : key.status === "EXPIRED" ? "warning" : "success";
        return <Tag color={color}>{label}</Tag>;
      },
    },
    { title: "最近使用", dataIndex: "lastUsedAt", render: (v: string | null) => (v ? formatRelative(v) : "从未使用") },
    { title: "过期时间", dataIndex: "expiresAt", render: (v: string | null) => (v ? formatDateTime(v).slice(0, 10) : "永不过期") },
    {
      title: "操作",
      key: "action",
      render: (_, key) =>
        key.status !== "REVOKED" && key.status !== "EXPIRED" ? (
          <Button type="link" danger onClick={() => setRevokeTarget(key)}>
            吊销
          </Button>
        ) : (
          "—"
        ),
    },
  ];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · API Key</h1>
          <p className="page-desc">机器访问必须有 scope、知识库范围与有效期；明文只展示一次</p>
        </div>
        <div className="page-actions">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
            创建 API Key
          </Button>
        </div>
      </div>

      {keys.loading ? (
        <Card>
          <SkeletonRows rows={4} />
        </Card>
      ) : keys.error ? (
        <Card>
          <ErrorState message={keys.error} onRetry={keys.reload} />
        </Card>
      ) : (
        <Card>
          <Table<ApiKey>
            rowKey="id"
            columns={columns}
            dataSource={allKeys}
            pagination={false}
            locale={{
              emptyText: (
                <Empty
                  icon="🔑"
                  title="暂无 API Key"
                  desc="为外部系统创建受限的机器访问凭证"
                  action={<Button type="primary" onClick={() => setCreateOpen(true)}>创建 API Key</Button>}
                />
              ),
            }}
          />
        </Card>
      )}

      <Modal
        title="创建 API Key"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
        okText={creating ? "创建中…" : "创建"}
        confirmLoading={creating}
      >
        <Form<CreateFormValues> form={createForm} layout="vertical" requiredMark={false} onFinish={handleCreate} initialValues={{ expireDays: 90 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入 Key 名称" }]}>
            <Input placeholder="例如：BI 报表同步" />
          </Form.Item>
          <Form.Item name="scopes" label="Scope" rules={[{ required: true, message: "至少选择一个 scope" }]}>
            <Select mode="multiple" placeholder="选择授权范围" options={SCOPE_OPTIONS.map((s) => ({ value: s, label: s }))} />
          </Form.Item>
          <Form.Item name="kbId" label="知识库范围">
            <Select
              allowClear
              placeholder="按调用者授权（推荐）"
              options={(kbs.data?.items ?? []).map((kb) => ({ value: kb.id, label: kb.name }))}
            />
            <div className="field-hint">机器凭证无法访问未授权知识库，即使 scope 允许</div>
          </Form.Item>
          <Form.Item name="expireDays" label="有效期" style={{ marginBottom: 0 }}>
            <Select
              options={[
                { value: 30, label: "30 天" },
                { value: 90, label: "90 天" },
                { value: 180, label: "180 天" },
                { value: 365, label: "1 年" },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="API Key 创建成功"
        open={newSecret !== null}
        onCancel={() => setNewSecret(null)}
        footer={<Button type="primary" onClick={() => setNewSecret(null)}>我已妥善保存</Button>}
      >
        <p style={{ color: "var(--danger)", marginBottom: 12, fontSize: 13 }}>
          明文仅展示这一次，关闭后无法再次查看。服务端只保存不可逆摘要。
        </p>
        <Space.Compact style={{ width: "100%" }}>
          <Input readOnly value={newSecret ?? ""} onFocus={(e) => e.target.select()} />
          <Button icon={<CopyOutlined />} onClick={() => void copySecret()}>
            复制
          </Button>
        </Space.Compact>
      </Modal>

      <Modal
        title="吊销 API Key"
        open={revokeTarget !== null}
        okText="确认吊销"
        cancelText="取消"
        confirmLoading={revoking}
        onOk={confirmRevoke}
        onCancel={() => setRevokeTarget(null)}
        okButtonProps={{ danger: true }}
      >
        吊销「<strong>{revokeTarget?.name}</strong>」（{revokeTarget?.keyPrefix}…）后，使用该凭证的调用将立即被拒绝。此操作不可撤销。
      </Modal>
    </div>
  );
}
