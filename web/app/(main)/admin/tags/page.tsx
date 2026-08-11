"use client";

import { useState } from "react";
import { Button, Card, Form, Input, Modal, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { Tag as TagType } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { useAsync } from "@/lib/use-async";

export default function TagsPage() {
  const toast = useToast();
  const [form] = Form.useForm<{ name: string }>();
  const tags = useAsync(() => api.listTags());
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<TagType | null>(null);
  const [deleting, setDeleting] = useState(false);

  const create = async (values: { name: string }) => {
    setCreating(true);
    try {
      await api.createTag(values.name);
      toast("success", "标签已创建");
      setCreateOpen(false);
      form.resetFields();
      tags.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreating(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await api.deleteTag(deleteTarget.id);
      toast("success", `标签「${deleteTarget.name}」已删除`);
      setDeleteTarget(null);
      tags.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  const columns: TableColumnsType<TagType> = [
    { title: "标签", dataIndex: "name", render: (v: string) => <Tag color="blue">{v}</Tag> },
    { title: "文档数", dataIndex: "documentCount", width: 100 },
    {
      title: "操作",
      key: "action",
      width: 90,
      render: (_, tag) => (
        <Button type="link" danger onClick={() => setDeleteTarget(tag)}>
          删除
        </Button>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: "flex", justifyContent: "flex-end", marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建标签
        </Button>
      </div>

      {tags.loading ? (
        <Card><SkeletonRows rows={4} /></Card>
      ) : tags.error ? (
        <Card><ErrorState message={tags.error} onRetry={tags.reload} /></Card>
      ) : (
        <Card>
          <Table<TagType>
            rowKey="id"
            columns={columns}
            dataSource={tags.data ?? []}
            pagination={false}
            locale={{ emptyText: <Empty icon="🏷️" title="暂无标签" desc="标签用于跨知识库的文档分类与筛选" /> }}
          />
        </Card>
      )}

      <Modal
        title="新建标签"
        open={createOpen}
        okText={creating ? "创建中…" : "创建"}
        cancelText="取消"
        confirmLoading={creating}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => void create(v as { name: string })}>
          <Form.Item name="name" label="标签名称" rules={[{ required: true, message: "请输入标签名称" }]}>
            <Input placeholder="例如：架构设计" maxLength={20} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="删除标签"
        open={deleteTarget !== null}
        okText="确认删除"
        cancelText="取消"
        confirmLoading={deleting}
        okButtonProps={{ danger: true }}
        onCancel={() => setDeleteTarget(null)}
        onOk={() => void confirmDelete()}
      >
        删除标签「<strong>{deleteTarget?.name}</strong>」后，文档上的该标签将被移除，但文档本身不受影响。
      </Modal>
    </>
  );
}
