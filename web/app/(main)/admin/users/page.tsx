"use client";

import { useState } from "react";
import { Avatar, Button, Card, Modal, Pagination, Table, Tabs, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { User } from "@/api-client";
import { Empty, ErrorState } from "@/components/async-state";
import { OrgTreeEditor } from "@/components/org-tree-editor";
import { useToast } from "@/components/feedback";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

type TabKey = "users" | "orgs";

export default function AdminUsersPage() {
  const toast = useToast();
  const [tab, setTab] = useState<TabKey>("users");
  const [page, setPage] = useState(1);
  const [disableTarget, setDisableTarget] = useState<{ id: number; name: string } | null>(null);
  const [disabling, setDisabling] = useState(false);

  const users = useAsync(() => api.listUsers({ page, size: 10 }), [page]);

  const columns: TableColumnsType<User> = [
    {
      title: "成员",
      key: "name",
      render: (_, u) => (
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <Avatar size={28}>{u.name.slice(0, 1)}</Avatar>
          <div>
            <div style={{ fontWeight: 500 }}>{u.name}</div>
            <div style={{ fontSize: 12, color: "var(--text-3)" }}>{u.email}</div>
          </div>
        </div>
      ),
    },
    { title: "组织", dataIndex: "orgName" },
    { title: "角色", dataIndex: "role", render: (r: string) => <Tag color="blue">{r}</Tag> },
    {
      title: "状态",
      key: "status",
      render: (_, u) => <Tag color={u.status === "DISABLED" ? "error" : "success"}>{u.status === "DISABLED" ? "已停用" : "正常"}</Tag>,
    },
    { title: "最近登录", dataIndex: "lastLoginAt", render: (v: string) => formatDateTime(v) },
    {
      title: "操作",
      key: "action",
      render: (_, u) =>
        u.status === "DISABLED" ? (
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
        ),
    },
  ];

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

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · 成员与组织</h1>
          <p className="page-desc">全局用户 + 租户成员分离；停用成员在 SLA 内失去访问</p>
        </div>
        <div className="page-actions">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => toast("info", "mock：邀请成员（SCIM 同步为 P1 能力）")}>
            邀请成员
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
    </div>
  );
}
