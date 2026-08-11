"use client";

import Link from "next/link";
import { useState } from "react";
import { Button, Card, Input, Modal, Pagination, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { CheckOutlined, CloseOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { ReviewItem } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

type ReviewAction = { kind: "APPROVED" | "REJECTED"; ids: number[] };

export default function ReviewPage() {
  const toast = useToast();
  const [page, setPage] = useState(1);
  const reviews = useAsync(() => api.listReviews({ page, size: 10 }), [page]);

  const [handled, setHandled] = useState<Record<number, "APPROVED" | "REJECTED">>({});
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [action, setAction] = useState<ReviewAction | null>(null);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const items = (reviews.data?.items ?? []).filter((item) => !handled[item.documentId]);

  const submitAction = async () => {
    if (!action) return;
    if (action.kind === "REJECTED" && !comment.trim()) {
      toast("error", "驳回必须填写审核意见");
      return;
    }
    setSubmitting(true);
    try {
      if (action.kind === "APPROVED") {
        await api.approveReviews(action.ids, comment);
      } else {
        await api.rejectReviews(action.ids, comment);
      }
      toast("success", `已${action.kind === "APPROVED" ? "通过" : "驳回"} ${action.ids.length} 篇文档`);
      setSelected(new Set());
      setComment("");
      setAction(null);
      setHandled({});
      reviews.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "操作失败");
    } finally {
      setSubmitting(false);
    }
  };

  const columns: TableColumnsType<ReviewItem> = [
    {
      title: "文档",
      dataIndex: "title",
      render: (v: string, item) => (
        <Link href={`/documents/${item.documentId}`} style={{ color: "var(--primary)", fontWeight: 500 }}>
          {v}
        </Link>
      ),
    },
    { title: "知识库", dataIndex: "kbName" },
    {
      title: "敏感级",
      dataIndex: "sensitivity",
      width: 90,
      render: (v: ReviewItem["sensitivity"]) => {
        const [label, color] = statusText("sensitivity", v);
        return <Tag color={color}>{label}</Tag>;
      },
    },
    { title: "提交人", dataIndex: "submitter", width: 100 },
    { title: "提交时间", dataIndex: "submittedAt", width: 120, render: (v: string) => formatRelative(v) },
    { title: "意见", dataIndex: "commentCount", width: 70, render: (v: number) => `${v} 条` },
    {
      title: "操作",
      key: "action",
      width: 150,
      render: (_, item) => (
        <div style={{ display: "flex", gap: 6 }}>
          <Button size="small" icon={<CheckOutlined />} onClick={() => setAction({ kind: "APPROVED", ids: [item.documentId] })}>
            通过
          </Button>
          <Button size="small" danger icon={<CloseOutlined />} onClick={() => setAction({ kind: "REJECTED", ids: [item.documentId] })}>
            驳回
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8, marginBottom: 16 }}>
        <Button
          icon={<CheckOutlined />}
          disabled={selected.size === 0}
          onClick={() => setAction({ kind: "APPROVED", ids: [...selected] })}
        >
          批量通过{selected.size ? ` (${selected.size})` : ""}
        </Button>
        <Button danger disabled={selected.size === 0} onClick={() => setAction({ kind: "REJECTED", ids: [...selected] })}>
          批量驳回{selected.size ? ` (${selected.size})` : ""}
        </Button>
      </div>

      {reviews.loading ? (
        <Card>
          <SkeletonRows rows={5} height={60} />
        </Card>
      ) : reviews.error ? (
        <Card>
          <ErrorState message={reviews.error} onRetry={reviews.reload} />
        </Card>
      ) : (
        <Card>
          <Table<ReviewItem>
            rowKey="documentId"
            columns={columns}
            dataSource={items}
            rowSelection={{
              selectedRowKeys: [...selected],
              onChange: (keys) => setSelected(new Set(keys as number[])),
            }}
            pagination={false}
            locale={{ emptyText: <Empty icon="✅" title="审核队列已清空" desc="新提交的内容将出现在这里" /> }}
          />
          {reviews.data ? (
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
              <Pagination
                current={page}
                pageSize={10}
                total={reviews.data.total - Object.keys(handled).length}
                onChange={setPage}
                showTotal={(t) => `共 ${t} 条`}
              />
            </div>
          ) : null}
        </Card>
      )}

      <Modal
        title={action?.kind === "APPROVED" ? "通过审核" : "驳回审核"}
        open={action !== null}
        onCancel={() => setAction(null)}
        okText={submitting ? "提交中…" : "确认提交"}
        cancelText="取消"
        confirmLoading={submitting}
        onOk={submitAction}
        okButtonProps={{ danger: action?.kind === "REJECTED" }}
      >
        <p style={{ marginBottom: 14, color: "var(--text-2)" }}>
          将对 {action?.ids.length ?? 0} 篇文档执行「{action?.kind === "APPROVED" ? "通过并发布" : "驳回"}」。
          {action?.kind === "APPROVED" ? "发布后进入在线索引，传播完成前按数据库策略二次过滤。" : "驳回后作者可修改重新提交。"}
        </p>
        <Input.TextArea
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="填写审核意见…"
          rows={4}
        />
      </Modal>
    </>
  );
}
