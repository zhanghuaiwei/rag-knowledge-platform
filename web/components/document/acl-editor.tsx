"use client";

import { useState } from "react";
import { Button, Descriptions, Form, Modal, Select, Space, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { DeleteOutlined, PlusOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { AclEntry, AclPrincipalType, PermissionPoint } from "@/api-client";
import { ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { useAsync } from "@/lib/use-async";

const PRINCIPAL_LABEL: Record<AclPrincipalType, string> = {
  USER: "用户",
  ORG: "部门",
  ROLE: "系统角色",
  KB_ROLE: "知识库角色",
};

const PERMISSION_LABEL: Record<PermissionPoint, string> = {
  VIEW_EXCERPT: "摘要",
  VIEW_CONTENT: "原文",
  DOWNLOAD_ORIGINAL: "下载",
};

const SYSTEM_ROLES = ["TENANT_ADMIN", "KNOWLEDGE_ADMIN", "AUDITOR", "MEMBER"];
const KB_ROLES = ["OWNER", "EDITOR", "VIEWER"];

interface GrantFormValues {
  principalType: AclPrincipalType;
  principalId: string;
  permissions: PermissionPoint[];
}

/** 文档级 ACL 编辑器（白名单语义）：授权/移除主体并展示可解释的有效权限。 */
export function AclEditor({ documentId, kbId }: { documentId: number; kbId: number }) {
  const toast = useToast();
  const [form] = Form.useForm<GrantFormValues>();
  const entries = useAsync(() => api.listDocumentAcl(documentId), [documentId]);
  const users = useAsync(() => api.listUsers({ page: 1, size: 100 }));
  const orgs = useAsync(() => api.listOrgs());
  const kb = useAsync(() => api.getKb(kbId), [kbId]);
  const me = useAsync(() => api.getCurrentUser(), []);

  const kbRole = kb.data?.role ?? "";
  const userRoles = me.data?.roles ?? [];

  const [grantOpen, setGrantOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [removeEntry, setRemoveEntry] = useState<AclEntry | null>(null);
  const [removing, setRemoving] = useState(false);

  const persist = async (next: Omit<AclEntry, "id">[]) => {
    await api.setDocumentAcl(documentId, { entries: next });
    entries.reload();
  };

  const resolvePrincipalName = (type: AclPrincipalType, id: string): string => {
    if (type === "USER") return users.data?.items.find((u) => String(u.id) === id)?.name ?? id;
    if (type === "ORG") return orgs.data?.find((o) => String(o.id) === id)?.name ?? id;
    return id;
  };

  const grant = async (values: GrantFormValues) => {
    setSaving(true);
    try {
      const next = [...(entries.data ?? []).map((e) => ({ principalType: e.principalType, principalName: e.principalName, permissions: e.permissions }))];
      const principalName = resolvePrincipalName(values.principalType, values.principalId);
      if (next.some((e) => e.principalType === values.principalType && e.principalName === principalName)) {
        throw new Error("该主体已存在授权，请先移除再调整");
      }
      next.push({ principalType: values.principalType, principalName, permissions: values.permissions });
      await persist(next);
      toast("success", "ACL 已更新");
      setGrantOpen(false);
      form.resetFields();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "授权失败");
    } finally {
      setSaving(false);
    }
  };

  const confirmRemove = async () => {
    if (!removeEntry) return;
    setRemoving(true);
    try {
      const next = (entries.data ?? [])
        .filter((e) => e.id !== removeEntry.id)
        .map((e) => ({ principalType: e.principalType, principalName: e.principalName, permissions: e.permissions }));
      await persist(next);
      toast("success", "ACL 条目已移除");
      setRemoveEntry(null);
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "移除失败");
    } finally {
      setRemoving(false);
    }
  };

  const principalType = Form.useWatch("principalType", form);
  const principalOptions =
    principalType === "USER"
      ? (users.data?.items ?? []).map((u) => ({ value: String(u.id), label: `${u.name}（${u.email}）` }))
      : principalType === "ORG"
        ? (orgs.data ?? []).map((o) => ({ value: String(o.id), label: o.name }))
        : principalType === "ROLE"
          ? SYSTEM_ROLES.map((r) => ({ value: r, label: r }))
          : KB_ROLES.map((r) => ({ value: r, label: r }));

  const columns: TableColumnsType<AclEntry> = [
    {
      title: "主体类型",
      dataIndex: "principalType",
      width: 100,
      render: (v: AclPrincipalType) => <Tag color="default">{PRINCIPAL_LABEL[v]}</Tag>,
    },
    { title: "主体", dataIndex: "principalName", render: (v: string) => <strong>{v}</strong> },
    {
      title: "权限点",
      dataIndex: "permissions",
      render: (perms: PermissionPoint[]) => (
        <Space size={4} wrap>
          {perms.map((p) => (
            <Tag key={p} color="processing">{PERMISSION_LABEL[p]}</Tag>
          ))}
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      width: 90,
      render: (_, e) => (
        <Button size="small" type="link" danger icon={<DeleteOutlined />} onClick={() => setRemoveEntry(e)}>
          移除
        </Button>
      ),
    },
  ];

  return (
    <div className="card">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h4 style={{ margin: 0 }}>文档级权限（ACL）</h4>
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => setGrantOpen(true)}>
          授权
        </Button>
      </div>

      <Descriptions
        size="small"
        column={1}
        items={[
          { key: "kb", label: "知识库角色", children: kbRole || "—" },
          { key: "tenant", label: "系统角色", children: userRoles.join(" · ") || "—" },
        ]}
        style={{ marginBottom: 12 }}
      />
      <p style={{ fontSize: 12, color: "var(--text-3)", marginBottom: 12 }}>
        有效权限 = 系统角色 ⊕ 知识库角色 ⊕ 文档 ACL；无 ACL 记录时文档继承知识库权限，有记录时按记录判定（白名单）。
      </p>

      {entries.loading ? (
        <SkeletonRows rows={2} />
      ) : entries.error ? (
        <ErrorState message={entries.error} onRetry={entries.reload} />
      ) : (
        <Table<AclEntry>
          rowKey="id"
          columns={columns}
          dataSource={entries.data ?? []}
          pagination={false}
          size="small"
          locale={{ emptyText: "无单独 ACL 记录，文档继承知识库成员权限" }}
        />
      )}

      <Modal
        title="授权访问"
        open={grantOpen}
        okText={saving ? "保存中…" : "授权"}
        cancelText="取消"
        confirmLoading={saving}
        onCancel={() => setGrantOpen(false)}
        onOk={() => form.submit()}
      >
        <Form<GrantFormValues> form={form} layout="vertical" requiredMark={false} onFinish={(v) => void grant(v)}>
          <Form.Item name="principalType" label="主体类型" rules={[{ required: true }]}>
            <Select
              placeholder="选择主体类型"
              options={(Object.keys(PRINCIPAL_LABEL) as AclPrincipalType[]).map((t) => ({ value: t, label: PRINCIPAL_LABEL[t] }))}
            />
          </Form.Item>
          <Form.Item name="principalId" label="主体" rules={[{ required: true, message: "请选择主体" }]}>
            <Select showSearch optionFilterProp="label" placeholder="选择主体" options={principalOptions} />
          </Form.Item>
          <Form.Item name="permissions" label="权限点" rules={[{ required: true, message: "至少选择一个权限点" }]}>
            <Select
              mode="multiple"
              placeholder="选择权限点"
              options={(Object.keys(PERMISSION_LABEL) as PermissionPoint[]).map((p) => ({ value: p, label: PERMISSION_LABEL[p] }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="移除授权"
        open={removeEntry !== null}
        okText="确认移除"
        cancelText="取消"
        confirmLoading={removing}
        okButtonProps={{ danger: true }}
        onCancel={() => setRemoveEntry(null)}
        onOk={() => void confirmRemove()}
      >
        移除「<strong>{removeEntry?.principalName}</strong>」的授权后，该主体将退回知识库成员权限判定。
      </Modal>
    </div>
  );
}
