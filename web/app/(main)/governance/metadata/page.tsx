"use client";

import { useState } from "react";
import { Button, Card, Checkbox, Form, Input, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined, SendOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { MetadataField, MetadataSchema, MetadataFieldType } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatRelative } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

/** 字段目录：新建 schema 时从中挑选字段并标记必填。 */
const FIELD_CATALOG: { key: string; label: string; type: MetadataFieldType; options?: string[] }[] = [
  { key: "owner", label: "内容所有者", type: "STRING" },
  { key: "domain", label: "业务域", type: "ENUM", options: ["产品研发", "市场营销", "人力资源", "财务合规", "客户服务"] },
  { key: "sensitivity", label: "敏感级", type: "ENUM", options: ["公开", "内部", "机密", "绝密"] },
  { key: "tags", label: "标签", type: "MULTI_VALUE" },
  { key: "reviewDate", label: "复审日期", type: "DATE" },
  { key: "jurisdiction", label: "适用法域", type: "ENUM", options: ["中国", "欧盟", "美国"] },
  { key: "holdYears", label: "保全年限", type: "STRING" },
];

const TYPE_LABEL: Record<MetadataFieldType, string> = {
  STRING: "文本",
  ENUM: "枚举",
  DATE: "日期",
  MULTI_VALUE: "多值",
  REFERENCE: "引用",
};

interface CreateFormValues {
  name: string;
  description?: string;
  fields: string[];
  required: string[];
}

export default function MetadataPage() {
  const toast = useToast();
  const [form] = Form.useForm<CreateFormValues>();
  const schemas = useAsync(() => api.listMetadataSchemas());
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const create = async (values: CreateFormValues) => {
    setCreating(true);
    try {
      const fields: MetadataField[] = FIELD_CATALOG.filter((f) => values.fields.includes(f.key)).map((f) => ({
        key: f.key,
        label: f.label,
        type: f.type,
        required: values.required.includes(f.key),
        options: f.options,
      }));
      if (fields.length === 0) throw new Error("至少选择一个字段");
      await api.createMetadataSchema({ name: values.name, description: values.description, fields });
      toast("success", "元数据 schema 已创建（草稿）");
      setCreateOpen(false);
      form.resetFields();
      schemas.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const publish = async (id: number) => {
    try {
      await api.publishMetadataSchema(id);
      toast("success", "schema 已发布，新文档按该 schema 校验必填元数据");
      schemas.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "发布失败");
    }
  };

  const columns: TableColumnsType<MetadataSchema> = [
    { title: "名称", dataIndex: "name", render: (v: string) => <strong>{v}</strong> },
    { title: "描述", dataIndex: "description", ellipsis: true },
    {
      title: "字段",
      key: "fields",
      render: (_, s) => (
        <Space size={4} wrap>
          {s.fields.map((f) => (
            <Tag key={f.key} color={f.required ? "blue" : "default"}>
              {f.label}{f.required ? " *" : ""}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: "状态",
      dataIndex: "status",
      render: (v: MetadataSchema["status"]) => <Tag color={v === "PUBLISHED" ? "success" : "warning"}>{v === "PUBLISHED" ? "已发布" : "草稿"}</Tag>,
    },
    { title: "更新", dataIndex: "updatedAt", width: 120, render: (v: string) => formatRelative(v) },
    {
      title: "操作",
      key: "action",
      width: 100,
      render: (_, s) =>
        s.status === "DRAFT" ? (
          <Button size="small" icon={<SendOutlined />} onClick={() => void publish(s.id)}>
            发布
          </Button>
        ) : (
          "—"
        ),
    },
  ];

  return (
    <>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建 schema
        </Button>
      </div>

      {schemas.loading ? (
        <Card><SkeletonRows rows={4} /></Card>
      ) : schemas.error ? (
        <Card><ErrorState message={schemas.error} onRetry={schemas.reload} /></Card>
      ) : (
        <Card>
          <Table<MetadataSchema>
            rowKey="id"
            columns={columns}
            dataSource={schemas.data ?? []}
            pagination={false}
            locale={{ emptyText: <Empty icon="📋" title="暂无元数据 schema" /> }}
          />
          <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 12 }}>
            新增必填字段可能导致存量文档不再满足发布条件；schema 发布前请评估影响（GKB-04）。
          </p>
        </Card>
      )}

      <Modal
        title="新建元数据 schema"
        open={createOpen}
        okText={creating ? "创建中…" : "创建"}
        cancelText="取消"
        confirmLoading={creating}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
        width={560}
      >
        <Form<CreateFormValues> form={form} layout="vertical" requiredMark={false} onFinish={(v) => void create(v)}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
            <Input placeholder="例如：标准文档元数据" maxLength={40} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea placeholder="说明适用范围与必填要求…" maxLength={200} rows={2} />
          </Form.Item>
          <Form.Item name="fields" label="包含字段" rules={[{ required: true, message: "请选择字段" }]}>
            <Checkbox.Group options={FIELD_CATALOG.map((f) => ({ value: f.key, label: f.label }))} />
          </Form.Item>
          <Form.Item name="required" label="必填字段">
            <Select
              mode="multiple"
              placeholder="选择必填字段"
              options={FIELD_CATALOG.map((f) => ({ value: f.key, label: f.label }))}
            />
          </Form.Item>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            字段类型：{FIELD_CATALOG.map((f) => `${f.label}（${TYPE_LABEL[f.type]}）`).join(" · ")}
          </Typography.Text>
        </Form>
      </Modal>
    </>
  );
}
