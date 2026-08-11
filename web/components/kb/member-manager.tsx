"use client";

import { useState } from "react";
import { Button, Card, Form, List, Modal, Select, Tag } from "antd";
import { UserAddOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { KbMember, KbMemberRole, User } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const ROLE_OPTIONS: KbMemberRole[] = ["OWNER", "EDITOR", "VIEWER"];

interface InviteForm {
  userId: number;
  role: KbMemberRole;
}

/** 知识库成员管理：邀请 / 移除 / 角色变更（OWNER 权限），写操作落地 mock 库。 */
export function MemberManager({ kbId, isOwner }: { kbId: number; isOwner: boolean }) {
  const toast = useToast();
  const [form] = Form.useForm<InviteForm>();
  const members = useAsync(() => api.listKbMembers(kbId), [kbId]);
  const users = useAsync(() => api.listUsers({ page: 1, size: 100 }), []);

  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviting, setInviting] = useState(false);
  const [removeTarget, setRemoveTarget] = useState<KbMember | null>(null);
  const [removing, setRemoving] = useState(false);
  const [roleBusy, setRoleBusy] = useState<number | null>(null);

  const allUsers = users.data?.items ?? [];

  const invite = async (values: InviteForm) => {
    setInviting(true);
    try {
      await api.addKbMember(kbId, { userId: values.userId, role: values.role });
      toast("success", "成员已邀请");
      setInviteOpen(false);
      form.resetFields();
      members.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "邀请失败");
    } finally {
      setInviting(false);
    }
  };

  const changeRole = async (member: KbMember, role: KbMemberRole) => {
    setRoleBusy(member.userId);
    try {
      await api.updateKbMemberRole(kbId, member.userId, role);
      toast("success", `已将「${member.userName}」设为${statusText("kbRole", role)[0]}`);
      members.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "角色变更失败");
    } finally {
      setRoleBusy(null);
    }
  };

  const confirmRemove = async () => {
    if (!removeTarget) return;
    setRemoving(true);
    try {
      await api.removeKbMember(kbId, removeTarget.userId);
      toast("success", `已移除「${removeTarget.userName}」`);
      setRemoveTarget(null);
      members.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "移除失败");
    } finally {
      setRemoving(false);
    }
  };

  if (members.loading) return <Card><SkeletonRows rows={4} /></Card>;
  if (members.error) return <Card><ErrorState message={members.error} onRetry={members.reload} /></Card>;

  const memberList = members.data ?? [];

  return (
    <Card
      title="成员与角色"
      extra={
        isOwner ? (
          <Button type="primary" size="small" icon={<UserAddOutlined />} onClick={() => setInviteOpen(true)}>
            邀请成员
          </Button>
        ) : null
      }
    >
      {memberList.length === 0 ? (
        <Empty icon="👥" title="暂无成员" desc="邀请成员开始协作" />
      ) : (
        <List
          dataSource={memberList}
          renderItem={(m) => (
            <List.Item
              actions={
                isOwner && m.role !== "OWNER" ? [
                  <Button key="remove" size="small" type="link" danger onClick={() => setRemoveTarget(m)}>
                    移除
                  </Button>,
                ] : []
              }
            >
              <List.Item.Meta title={m.userName} description={m.userId === 1 ? "（当前用户）" : undefined} />
              <Select<KbMemberRole>
                size="small"
                value={m.role}
                disabled={!isOwner || m.role === "OWNER" || roleBusy === m.userId}
                loading={roleBusy === m.userId}
                options={ROLE_OPTIONS.map((r) => ({ value: r, label: statusText("kbRole", r)[0] }))}
                onChange={(role) => void changeRole(m, role)}
                style={{ width: 96 }}
              />
              <Tag color={statusText("kbRole", m.role)[1]} style={{ marginLeft: 8 }}>{statusText("kbRole", m.role)[0]}</Tag>
            </List.Item>
          )}
        />
      )}

      <Modal
        title="邀请成员"
        open={inviteOpen}
        okText={inviting ? "邀请中…" : "邀请"}
        cancelText="取消"
        confirmLoading={inviting}
        onCancel={() => setInviteOpen(false)}
        onOk={() => form.submit()}
      >
        <Form<InviteForm> form={form} layout="vertical" requiredMark={false} onFinish={(v) => void invite(v)}>
          <Form.Item name="userId" label="成员" rules={[{ required: true, message: "请选择成员" }]}>
            <Select
              placeholder="从租户成员中选择"
              showSearch
              optionFilterProp="label"
              options={allUsers
                .filter((u: User) => u.status === "ACTIVE")
                .map((u: User) => ({ value: u.id, label: `${u.name}（${u.email}）` }))}
            />
          </Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true, message: "请选择角色" }]} initialValue="EDITOR">
            <Select options={ROLE_OPTIONS.map((r) => ({ value: r, label: statusText("kbRole", r)[0] }))} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="移除成员"
        open={removeTarget !== null}
        okText="确认移除"
        cancelText="取消"
        confirmLoading={removing}
        okButtonProps={{ danger: true }}
        onCancel={() => setRemoveTarget(null)}
        onOk={() => void confirmRemove()}
      >
        移除「<strong>{removeTarget?.userName}</strong>」后，其对该知识库的查看与编辑权限立即失效。
      </Modal>
    </Card>
  );
}
