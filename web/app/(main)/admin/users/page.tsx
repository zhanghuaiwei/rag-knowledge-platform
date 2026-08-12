"use client";

import { useState } from "react";
import { Avatar, Button, Card, Form, Input, Modal, Pagination, Select, Space, Table, Tabs, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { UserAddOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { TenantRole, User } from "@/api-client";
import { Empty, ErrorState } from "@/components/async-state";
import { OrgTreeEditor } from "@/components/org-tree-editor";
import { useToast } from "@/components/feedback";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

type TabKey = "users" | "orgs";

const TENANT_ROLES: TenantRole[] = ["TENANT_ADMIN", "SECURITY_ADMIN", "KNOWLEDGE_ADMIN", "AUDITOR", "MEMBER"];

const ROLE_LABEL: Record<TenantRole, string> = {
  TENANT_ADMIN: "租户管理员",
  SECURITY_ADMIN: "安全管理员",
  KNOWLEDGE_ADMIN: "知识库管理员",
  AUDITOR: "审计员",
  MEMBER: "成员",
};

const ROLE_COLOR: Record<TenantRole, string> = {
  TENANT_ADMIN: "purple",
  SECURITY_ADMIN: "volcano",
  KNOWLEDGE_ADMIN: "geekblue",
  AUDITOR: "cyan",
  MEMBER: "default",
};

interface CreateUserForm {
  username: string;
  email: string;
  displayName: string;
  password: string;
  roles: TenantRole[];
}

export default function AdminUsersPage() {
  const toast = useToast();
  const [tab, setTab] = useState<TabKey>("users");
  const [page, setPage] = useState(1);

  const [createOpen, setCreateOpen] = useState(false);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [createForm] = Form.useForm<CreateUserForm>();

  const [roleTarget, setRoleTarget] = useState<User | null>(null);
  const [roleSubmitting, setRoleSubmitting] = useState(false);
  const [roleForm] = Form.useForm<{ roles: TenantRole[] }>();

  const [resetTarget, setResetTarget] = useState<User | null>(null);
  const [resetSubmitting, setResetSubmitting] = useState(false);
  const [resetForm] = Form.useForm<{ newPassword: string }>();

  const [disableTarget, setDisableTarget] = useState<{ id: number; name: string } | null>(null);
  const [disabling, setDisabling] = useState(false);

  const [removeTarget, setRemoveTarget] = useState<User | null>(null);
  const [removing, setRemoving] = useState(false);

  const users = useAsync(() => api.listUsers({ page, size: 10 }), [page]);

  const columns: TableColumnsType<User> = [
    {
      title: "成员",
      key: "name",
      render: (_, u) => (
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Avatar size={28}>{u.name.slice(0, 1)}</Avatar>
          <div>
            <div style={{ fontWeight: 500 }}>
              {u.name}
              {u.mustChangePassword ? (
                <Tag color="warning" style={{ marginLeft: 8 }}>
                  待改密
                </Tag>
              ) : null}
            </div>
            <div style={{ fontSize: 12, color: "var(--text-3)" }}>{u.email}</div>
          </div>
        </div>
      ),
    },
    { title: "组织", dataIndex: "orgName" },
    {
      title: "角色",
      key: "roles",
      render: (_, u) => (
        <Space size={4} wrap>
          {u.roles.map((r) => (
            <Tag key={r} color={ROLE_COLOR[r]}>
              {ROLE_LABEL[r]}
            </Tag>
          ))}
        </Space>
      ),
    },
    {
      title: "状态",
      key: "status",
      render: (_, u) => (
        <Tag color={u.status === "DISABLED" ? "error" : "success"}>{u.status === "DISABLED" ? "已停用" : "正常"}</Tag>
      ),
    },
    { title: "最近登录", dataIndex: "lastLoginAt", render: (v: string | null) => formatDateTime(v) },
    {
      title: "操作",
      key: "action",
      render: (_, u) => (
        <Space size={0} wrap>
          {u.status === "DISABLED" ? (
            <Button
              type="link"
              onClick={async () => {
                try {
                  await api.enableUser(u.id);
                  toast("success", `已恢复 ${u.name}`);
                  users.reload();
                } catch (err: unknown) {
                  toast("error", err instanceof Error ? err.message : "恢复失败");
                }
              }}
            >
              恢复
            </Button>
          ) : (
            <Button type="link" danger onClick={() => setDisableTarget({ id: u.id, name: u.name })}>
              停用
            </Button>
          )}
          <Button type="link" onClick={() => openRoleEditor(u)}>
            编辑角色
          </Button>
          <Button type="link" onClick={() => openResetPassword(u)}>
            重置密码
          </Button>
          <Button type="link" danger onClick={() => setRemoveTarget(u)}>
            移出
          </Button>
        </Space>
      ),
    },
  ];

  const openRoleEditor = (u: User) => {
    setRoleTarget(u);
    roleForm.setFieldsValue({ roles: u.roles });
  };

  const openResetPassword = (u: User) => {
    setResetTarget(u);
    resetForm.resetFields();
  };

  const confirmCreate = async () => {
    const values = await createForm.validateFields();
    setCreateSubmitting(true);
    try {
      await api.createUser(values);
      toast("success", `已创建用户 ${values.displayName}，首登需修改初始密码`);
      setCreateOpen(false);
      createForm.resetFields();
      users.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreateSubmitting(false);
    }
  };

  const confirmRoles = async () => {
    if (!roleTarget) return;
    const values = await roleForm.validateFields();
    setRoleSubmitting(true);
    try {
      await api.setRoles(roleTarget.id, values.roles);
      toast("success", `已更新 ${roleTarget.name} 的角色`);
      setRoleTarget(null);
      users.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "角色更新失败");
    } finally {
      setRoleSubmitting(false);
    }
  };

  const confirmResetPassword = async () => {
    if (!resetTarget) return;
    const values = await resetForm.validateFields();
    setResetSubmitting(true);
    try {
      await api.resetPassword(resetTarget.id, { newPassword: values.newPassword });
      toast("success", `已重置 ${resetTarget.name} 的密码，首登需改密`);
      setResetTarget(null);
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "重置失败");
    } finally {
      setResetSubmitting(false);
    }
  };

  const confirmDisable = async () => {
    if (!disableTarget) return;
    setDisabling(true);
    try {
      await api.disableUser(disableTarget.id);
      toast("success", `已停用 ${disableTarget.name}，其会话与 API 访问将在目标 SLA 内失效`);
      setDisableTarget(null);
      users.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "停用失败");
    } finally {
      setDisabling(false);
    }
  };

  const confirmRemove = async () => {
    if (!removeTarget) return;
    setRemoving(true);
    try {
      await api.removeUser(removeTarget.id);
      toast("success", `已将 ${removeTarget.name} 移出当前租户`);
      setRemoveTarget(null);
      users.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "移出失败");
    } finally {
      setRemoving(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · 成员与组织</h1>
          <p className="page-desc">全局用户 + 租户成员分离；停用成员在 SLA 内失去访问</p>
        </div>
        <div className="page-actions">
          <Button type="primary" icon={<UserAddOutlined />} onClick={() => setCreateOpen(true)}>
            创建用户
          </Button>
        </div>
      </div>

      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as TabKey)}
        items={[
          { key: "users", label: "成员" },
          { key: "orgs", label: "组织架构" },
        ]}
      />

      {tab === "users" ? (
        <>
          <Card>
            <Table<User>
              rowKey="id"
              columns={columns}
              dataSource={users.data?.items ?? []}
              loading={users.loading}
              pagination={false}
              locale={{ emptyText: <Empty icon="👥" title="暂无成员" /> }}
            />
            {users.data ? (
              <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
                <Pagination current={page} pageSize={10} total={users.data.total} onChange={setPage} showTotal={(t) => `共 ${t} 条`} />
              </div>
            ) : null}
          </Card>
          {users.error ? (
            <Card style={{ marginTop: 12 }}>
              <ErrorState message={users.error} onRetry={users.reload} />
            </Card>
          ) : null}
        </>
      ) : (
        <OrgTreeEditor />
      )}

      {/* 创建用户 */}
      <Modal
        title="创建用户"
        open={createOpen}
        okText="创建"
        cancelText="取消"
        confirmLoading={createSubmitting}
        onCancel={() => setCreateOpen(false)}
        onOk={confirmCreate}
      >
        <Form<CreateUserForm> form={createForm} layout="vertical" requiredMark={false} initialValues={{ roles: ["MEMBER"] }}>
          <Form.Item name="username" label="登录账号" rules={[{ required: true, message: "请输入登录账号" }]}>
            <Input placeholder="如 zhangsan" />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="姓名"
            rules={[{ required: true, message: "请输入姓名" }]}
          >
            <Input placeholder="如 张三" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[
              { required: true, message: "请输入邮箱" },
              { type: "email", message: "邮箱格式不正确" },
            ]}
          >
            <Input placeholder="如 zhangsan@example.com" />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: "请输入初始密码" },
              { min: 6, message: "密码至少 6 位" },
            ]}
            extra="用户首次登录将被要求修改此密码"
          >
            <Input.Password placeholder="至少 6 位" />
          </Form.Item>
          <Form.Item name="roles" label="租户角色" rules={[{ required: true, message: "请至少选择一个角色" }]}>
            <Select
              mode="multiple"
              placeholder="选择角色"
              options={TENANT_ROLES.map((r) => ({ label: ROLE_LABEL[r], value: r }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑角色 */}
      <Modal
        title={roleTarget ? `编辑角色 · ${roleTarget.name}` : "编辑角色"}
        open={roleTarget !== null}
        okText="保存"
        cancelText="取消"
        confirmLoading={roleSubmitting}
        onCancel={() => setRoleTarget(null)}
        onOk={confirmRoles}
      >
        <Form form={roleForm} layout="vertical" requiredMark={false}>
          <Form.Item name="roles" label="租户角色（整体替换）" rules={[{ required: true, message: "请至少选择一个角色" }]}>
            <Select
              mode="multiple"
              placeholder="选择角色"
              options={TENANT_ROLES.map((r) => ({ label: ROLE_LABEL[r], value: r }))}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 重置密码 */}
      <Modal
        title={resetTarget ? `重置密码 · ${resetTarget.name}` : "重置密码"}
        open={resetTarget !== null}
        okText="重置"
        cancelText="取消"
        confirmLoading={resetSubmitting}
        onCancel={() => setResetTarget(null)}
        onOk={confirmResetPassword}
      >
        <Form form={resetForm} layout="vertical" requiredMark={false}>
          <Form.Item
            name="newPassword"
            label="临时密码"
            rules={[
              { required: true, message: "请输入临时密码" },
              { min: 6, message: "密码至少 6 位" },
            ]}
            extra="重置后用户首次登录将被要求修改此密码"
          >
            <Input.Password placeholder="至少 6 位" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 停用确认 */}
      <Modal
        title="停用成员"
        open={disableTarget !== null}
        okText="确认停用"
        cancelText="取消"
        confirmLoading={disabling}
        onCancel={() => setDisableTarget(null)}
        onOk={confirmDisable}
        okButtonProps={{ danger: true }}
      >
        <div>
          停用「<strong>{disableTarget?.name}</strong>」后：其登录态、API Key 调用与知识库访问将被回收；所属文档的所有者需另行交接。
        </div>
      </Modal>

      {/* 移出租户确认 */}
      <Modal
        title="移出当前租户"
        open={removeTarget !== null}
        okText="确认移出"
        cancelText="取消"
        confirmLoading={removing}
        onCancel={() => setRemoveTarget(null)}
        onOk={confirmRemove}
        okButtonProps={{ danger: true }}
      >
        <div>
          将「<strong>{removeTarget?.name}</strong>」移出当前租户后：其成员关系、角色与组织关联被移除（全局身份保留，可能仍可访问其他租户）。此操作写入审计日志。
        </div>
      </Modal>
    </div>
  );
}
