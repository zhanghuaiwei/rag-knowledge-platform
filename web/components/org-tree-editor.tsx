"use client";

import { useState } from "react";
import { Button, Card, Form, Input, Modal, Select, Space, Tree } from "antd";
import type { TreeDataNode } from "antd";
import { DeleteOutlined, EditOutlined, PlusOutlined, TeamOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { Org } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { useAsync } from "@/lib/use-async";

function buildTree(orgs: Org[]): TreeDataNode[] {
  const childrenOf = (parentId: number | null): TreeDataNode[] =>
    orgs
      .filter((org) => org.parentId === parentId)
      .map((org) => ({
        key: String(org.id),
        title: `${org.name}（${org.memberCount} 人）`,
        children: childrenOf(org.id),
      }));
  return childrenOf(null);
}

type ModalKind = "create" | "rename" | "assign" | null;

/** 组织架构树编辑器：新增/重命名/删除部门（仅叶子）+ 分配成员（F2.12）。 */
export function OrgTreeEditor() {
  const toast = useToast();
  const [form] = Form.useForm<{ name: string; parentId: number | null; userId: number }>();
  const orgs = useAsync(() => api.listOrgs());
  const users = useAsync(() => api.listUsers({ page: 1, size: 100 }));

  const [selectedKey, setSelectedKey] = useState<string | null>(null);
  const [modalKind, setModalKind] = useState<ModalKind>(null);
  const [busy, setBusy] = useState(false);

  const selectedOrg: Org | undefined = (orgs.data ?? []).find((o) => String(o.id) === selectedKey);

  const openCreate = (parentId: number | null) => {
    form.setFieldsValue({ name: "", parentId });
    setModalKind("create");
  };

  const submitCreate = async (values: { name: string }) => {
    setBusy(true);
    try {
      await api.createOrg({ name: values.name, parentId: selectedKey ? Number(selectedKey) : null });
      toast("success", "部门已创建");
      setModalKind(null);
      form.resetFields();
      orgs.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setBusy(false);
    }
  };

  const submitRename = async (values: { name: string }) => {
    if (!selectedOrg) return;
    setBusy(true);
    try {
      await api.updateOrg(selectedOrg.id, values.name);
      toast("success", "部门已重命名");
      setModalKind(null);
      orgs.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "重命名失败");
    } finally {
      setBusy(false);
    }
  };

  const submitAssign = async (values: { userId: number }) => {
    if (!selectedOrg) return;
    setBusy(true);
    try {
      await api.updateUserOrg(values.userId, selectedOrg.id);
      toast("success", "成员已分配到该部门");
      setModalKind(null);
      users.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "分配失败");
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = async () => {
    if (!selectedOrg) return;
    setBusy(true);
    try {
      await api.deleteOrg(selectedOrg.id);
      toast("success", "部门已删除");
      setSelectedKey(null);
      orgs.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "删除失败");
    } finally {
      setBusy(false);
    }
  };

  if (orgs.loading) return <Card><SkeletonRows rows={5} /></Card>;
  if (orgs.error) return <Card><ErrorState message={orgs.error} onRetry={orgs.reload} /></Card>;

  const treeData = buildTree(orgs.data ?? []);

  return (
    <Card
      title="组织架构"
      extra={
        <Space>
          <Button size="small" icon={<PlusOutlined />} onClick={() => openCreate(null)}>
            新增根部门
          </Button>
          <Button size="small" icon={<PlusOutlined />} disabled={!selectedOrg} onClick={() => selectedOrg && openCreate(selectedOrg.id)}>
            新增子部门
          </Button>
        </Space>
      }
    >
      {treeData.length === 0 ? (
        <Empty icon="🏢" title="暂无组织" desc="创建根部门开始搭建部门树" />
      ) : (
        <>
          <Tree
            treeData={treeData}
            defaultExpandAll
            selectedKeys={selectedKey ? [selectedKey] : []}
            onSelect={(keys) => setSelectedKey((keys[0] as string) ?? null)}
          />
          {selectedOrg ? (
            <Space style={{ marginTop: 12 }} wrap>
              <Button size="small" icon={<EditOutlined />} onClick={() => { form.setFieldsValue({ name: selectedOrg.name }); setModalKind("rename"); }}>
                重命名
              </Button>
              <Button size="small" icon={<TeamOutlined />} onClick={() => setModalKind("assign")}>
                分配成员
              </Button>
              <Button size="small" danger icon={<DeleteOutlined />} onClick={() => void confirmDelete()}>
                删除部门
              </Button>
              <span style={{ fontSize: 12, color: "var(--text-3)" }}>
                当前选中：{selectedOrg.name} · {selectedOrg.memberCount} 人
              </span>
            </Space>
          ) : null}
        </>
      )}

      <Modal
        title="新增部门"
        open={modalKind === "create"}
        okText={busy ? "创建中…" : "创建"}
        cancelText="取消"
        confirmLoading={busy}
        onCancel={() => setModalKind(null)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => void submitCreate(v as { name: string })}>
          <Form.Item name="name" label={selectedOrg ? `上级部门：${selectedOrg.name}` : "部门名称"} rules={[{ required: true, message: "请输入部门名称" }]}>
            <Input maxLength={30} placeholder="例如：研发中心" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重命名部门"
        open={modalKind === "rename"}
        okText={busy ? "保存中…" : "保存"}
        cancelText="取消"
        confirmLoading={busy}
        onCancel={() => setModalKind(null)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => void submitRename(v as { name: string })}>
          <Form.Item name="name" label="部门名称" rules={[{ required: true, message: "请输入部门名称" }]}>
            <Input maxLength={30} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="分配成员到部门"
        open={modalKind === "assign"}
        okText={busy ? "分配中…" : "分配"}
        cancelText="取消"
        confirmLoading={busy}
        onCancel={() => setModalKind(null)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => void submitAssign(v as { userId: number })}>
          <Form.Item name="userId" label={`目标部门：${selectedOrg?.name ?? ""}`} rules={[{ required: true, message: "请选择成员" }]}>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择成员"
              options={(users.data?.items ?? []).map((u) => ({ value: u.id, label: `${u.name}（${u.email}）` }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
