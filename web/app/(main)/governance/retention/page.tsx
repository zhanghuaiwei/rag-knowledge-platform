"use client";

import { useState } from "react";
import { Button, Card, Form, Input, Modal, Select, Switch, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined, StopOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { LegalHold, RetentionPolicy, RetentionPolicyInput } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatRelative } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const APPLIES_LABEL: Record<RetentionPolicy["appliesTo"], string> = {
  TENANT: "全租户",
  KB: "知识库",
  CATEGORY: "分类",
};

const ACTION_LABEL: Record<RetentionPolicy["action"], string> = {
  AUTO_EXPIRE: "自动过期",
  REVIEW: "进入复审",
  RETAIN: "长期保留",
};

interface PolicyFormValues {
  name: string;
  appliesTo: RetentionPolicyInput["appliesTo"];
  durationMonths: number;
  action: RetentionPolicyInput["action"];
}

interface HoldFormValues {
  name: string;
  reason: string;
  documentIds: number[];
}

export default function RetentionPage() {
  const toast = useToast();
  const [policyForm] = Form.useForm<PolicyFormValues>();
  const [holdForm] = Form.useForm<HoldFormValues>();
  const policies = useAsync(() => api.listRetentionPolicies());
  const holds = useAsync(() => api.listLegalHolds());
  const docs = useAsync(() => api.listDocuments({ page: 1, size: 100 }));

  const [policyOpen, setPolicyOpen] = useState(false);
  const [creatingPolicy, setCreatingPolicy] = useState(false);
  const [holdOpen, setHoldOpen] = useState(false);
  const [creatingHold, setCreatingHold] = useState(false);

  const createPolicy = async (values: PolicyFormValues) => {
    setCreatingPolicy(true);
    try {
      await api.createRetentionPolicy(values);
      toast("success", "保留策略已创建");
      setPolicyOpen(false);
      policyForm.resetFields();
      policies.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreatingPolicy(false);
    }
  };

  const togglePolicy = async (id: number, enabled: boolean) => {
    try {
      await api.toggleRetentionPolicy(id, enabled);
      policies.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "操作失败");
    }
  };

  const createHold = async (values: HoldFormValues) => {
    setCreatingHold(true);
    try {
      await api.createLegalHold(values);
      toast("success", "法律保全已生效，相关文档阻断清理与过期");
      setHoldOpen(false);
      holdForm.resetFields();
      holds.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreatingHold(false);
    }
  };

  const releaseHold = async (id: number) => {
    try {
      await api.releaseLegalHold(id);
      toast("success", "法律保全已解除");
      holds.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "解除失败");
    }
  };

  const policyColumns: TableColumnsType<RetentionPolicy> = [
    { title: "名称", dataIndex: "name", render: (v: string) => <strong>{v}</strong> },
    { title: "范围", dataIndex: "appliesTo", width: 110, render: (v: RetentionPolicy["appliesTo"]) => APPLIES_LABEL[v] },
    { title: "期限", dataIndex: "durationMonths", width: 90, render: (v: number) => `${v} 个月` },
    { title: "动作", dataIndex: "action", width: 110, render: (v: RetentionPolicy["action"]) => ACTION_LABEL[v] },
    {
      title: "启用",
      key: "enabled",
      width: 90,
      render: (_, p) => <Switch checked={p.enabled} size="small" onChange={(checked) => void togglePolicy(p.id, checked)} />,
    },
    { title: "创建", dataIndex: "createdAt", width: 110, render: (v: string) => formatRelative(v) },
  ];

  const holdColumns: TableColumnsType<LegalHold> = [
    { title: "保全名称", dataIndex: "name", render: (v: string) => <strong>{v}</strong> },
    { title: "原因", dataIndex: "reason", ellipsis: true },
    { title: "文档数", key: "count", width: 80, render: (_, h) => <Tag color="error">{h.documentIds.length}</Tag> },
    { title: "创建人", dataIndex: "createdBy", width: 90 },
    { title: "状态", dataIndex: "releasedAt", width: 90, render: (v: string | null) => <Tag color={v ? "default" : "error"}>{v ? "已解除" : "保全中"}</Tag> },
    { title: "创建时间", dataIndex: "createdAt", width: 110, render: (v: string) => formatRelative(v) },
    {
      title: "操作",
      key: "action",
      width: 90,
      render: (_, h) =>
        h.releasedAt ? (
          "—"
        ) : (
          <Button size="small" icon={<StopOutlined />} danger onClick={() => void releaseHold(h.id)}>
            解除
          </Button>
        ),
    },
  ];

  const docOptions = (docs.data?.items ?? []).map((d) => ({ value: d.id, label: d.fileName }));

  return (
    <>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginBottom: 16 }}>
        <Button icon={<PlusOutlined />} onClick={() => setPolicyOpen(true)}>
          新建保留策略
        </Button>
        <Button type="primary" icon={<StopOutlined />} onClick={() => setHoldOpen(true)}>
          发起法律保全
        </Button>
      </div>

      <Card title="保留策略" style={{ marginBottom: 16 }}>
        {policies.loading ? (
          <SkeletonRows rows={3} />
        ) : policies.error ? (
          <ErrorState message={policies.error} onRetry={policies.reload} />
        ) : (
          <Table<RetentionPolicy> rowKey="id" columns={policyColumns} dataSource={policies.data ?? []} pagination={false} size="small" />
        )}
      </Card>

      <Card title="法律保全" extra={<Typography.Text type="secondary" style={{ fontSize: 12 }}>保全对象阻断清理与过期</Typography.Text>}>
        {holds.loading ? (
          <SkeletonRows rows={2} />
        ) : holds.error ? (
          <ErrorState message={holds.error} onRetry={holds.reload} />
        ) : (holds.data?.length ?? 0) === 0 ? (
          <Empty icon="🛡️" title="暂无法律保全" />
        ) : (
          <Table<LegalHold> rowKey="id" columns={holdColumns} dataSource={holds.data ?? []} pagination={false} size="small" />
        )}
      </Card>

      <Modal
        title="新建保留策略"
        open={policyOpen}
        okText={creatingPolicy ? "创建中…" : "创建"}
        cancelText="取消"
        confirmLoading={creatingPolicy}
        onCancel={() => setPolicyOpen(false)}
        onOk={() => policyForm.submit()}
      >
        <Form<PolicyFormValues> form={policyForm} layout="vertical" requiredMark={false} onFinish={(v) => void createPolicy(v)}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
            <Input placeholder="例如：默认保留 3 年" maxLength={40} />
          </Form.Item>
          <Form.Item name="appliesTo" label="适用范围" rules={[{ required: true }]} initialValue="TENANT">
            <Select options={["TENANT", "KB", "CATEGORY"].map((v) => ({ value: v, label: APPLIES_LABEL[v as RetentionPolicy["appliesTo"]] }))} />
          </Form.Item>
          <Form.Item name="durationMonths" label="保留期限（月）" rules={[{ required: true }]} initialValue={36}>
            <Select options={[12, 24, 36, 60, 120].map((m) => ({ value: m, label: `${m} 个月` }))} />
          </Form.Item>
          <Form.Item name="action" label="到期动作" rules={[{ required: true }]} initialValue="REVIEW">
            <Select options={(["AUTO_EXPIRE", "REVIEW", "RETAIN"] as const).map((a) => ({ value: a, label: ACTION_LABEL[a] }))} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="发起法律保全"
        open={holdOpen}
        okText={creatingHold ? "创建中…" : "发起保全"}
        cancelText="取消"
        confirmLoading={creatingHold}
        onCancel={() => setHoldOpen(false)}
        onOk={() => holdForm.submit()}
      >
        <Form<HoldFormValues> form={holdForm} layout="vertical" requiredMark={false} onFinish={(v) => void createHold(v)}>
          <Form.Item name="name" label="保全名称" rules={[{ required: true, message: "请输入名称" }]}>
            <Input placeholder="例如：合规调查保全" maxLength={40} />
          </Form.Item>
          <Form.Item name="reason" label="保全原因" rules={[{ required: true, message: "请说明原因" }]}>
            <Input.TextArea rows={2} placeholder="法务依据或调查事由…" maxLength={200} />
          </Form.Item>
          <Form.Item name="documentIds" label="保全文档" rules={[{ required: true, message: "请选择文档" }]}>
            <Select mode="multiple" showSearch optionFilterProp="label" placeholder="选择受保全的文档" options={docOptions} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
