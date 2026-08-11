"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button, Card, Pagination, Segmented, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { PlusOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { Kb } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { formatNumber, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const roleTag = (role: Kb["role"]) => {
  const [label, color] = statusText("kbRole", role);
  return <Tag color={color}>{label}</Tag>;
};

export default function KbsPage() {
  const router = useRouter();
  const [page, setPage] = useState(1);
  const [view, setView] = useState<"card" | "table">("card");
  const kbs = useAsync(() => api.listKbs({ page, size: 9 }), [page]);

  const columns: TableColumnsType<Kb> = [
    {
      title: "名称",
      dataIndex: "name",
      render: (name: string) => <strong>{name}</strong>,
    },
    {
      title: "可见性",
      dataIndex: "visibility",
      render: (v: Kb["visibility"]) => (v === "PRIVATE" ? "私有" : "租户可见"),
    },
    {
      title: "文档/分块",
      key: "counts",
      render: (_, kb) => `${kb.documentCount} / ${formatNumber(kb.chunkCount)}`,
    },
    { title: "我的角色", dataIndex: "role", render: roleTag },
    {
      title: "状态",
      dataIndex: "status",
      render: (v: Kb["status"]) => <Tag color={v === "ACTIVE" ? "success" : "default"}>{v === "ACTIVE" ? "运行中" : v === "ARCHIVED" ? "已归档" : "删除中"}</Tag>,
    },
    { title: "更新时间", dataIndex: "updatedAt", render: (v: string) => formatRelative(v) },
  ];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">知识库</h1>
          <p className="page-desc">按业务域组织知识资产，治理策略随库生效</p>
        </div>
        <div className="page-actions">
          <Segmented
            value={view}
            onChange={(v) => setView(v as "card" | "table")}
            options={[
              { label: "卡片", value: "card" },
              { label: "表格", value: "table" },
            ]}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => router.push("/kbs/new")}>
            新建知识库
          </Button>
        </div>
      </div>

      {kbs.loading ? (
        <Card>
          <SkeletonRows rows={4} height={88} />
        </Card>
      ) : kbs.error ? (
        <Card>
          <ErrorState message={kbs.error} onRetry={kbs.reload} />
        </Card>
      ) : (kbs.data?.items.length ?? 0) === 0 ? (
        <Card>
          <Empty
            icon="📚"
            title="暂无知识库"
            desc="创建知识库后可上传文档或接入连接器"
            action={<Button type="primary" onClick={() => router.push("/kbs/new")}>新建知识库</Button>}
          />
        </Card>
      ) : view === "card" ? (
        <div className="grid grid-3">
          {(kbs.data?.items ?? []).map((kb) => (
            <Link
              key={kb.id}
              href={`/kbs/${kb.id}`}
              className="card card-hover"
              style={{ display: "flex", flexDirection: "column", gap: 10, color: "inherit" }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
                <strong style={{ fontSize: 15 }}>{kb.name}</strong>
                <Tag color={kb.status === "ACTIVE" ? "success" : kb.status === "ARCHIVED" ? "default" : "error"}>
                  {kb.status === "ACTIVE" ? "运行中" : kb.status === "ARCHIVED" ? "已归档" : "删除中"}
                </Tag>
              </div>
              <p style={{ color: "var(--text-2)", fontSize: 13, flex: 1, overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", margin: 0 }}>
                {kb.description}
              </p>
              <div style={{ display: "flex", gap: 14, color: "var(--text-3)", fontSize: 12, flexWrap: "wrap" }}>
                <span>{kb.documentCount} 文档</span>
                <span>{formatNumber(kb.chunkCount)} 分块</span>
                <span>{kb.visibility === "PRIVATE" ? "私有" : "租户可见"}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <Tag color={statusText("kbRole", kb.role)[1]}>我的角色：{statusText("kbRole", kb.role)[0]}</Tag>
                <span style={{ fontSize: 12, color: "var(--text-3)" }}>{formatRelative(kb.updatedAt)}更新</span>
              </div>
            </Link>
          ))}
        </div>
      ) : (
        <Card>
          <Table<Kb>
            rowKey="id"
            columns={columns}
            dataSource={kbs.data?.items ?? []}
            pagination={false}
            onRow={(kb) => ({ onClick: () => router.push(`/kbs/${kb.id}`) })}
            locale={{ emptyText: <Empty icon="📚" title="暂无知识库" /> }}
          />
        </Card>
      )}

      {kbs.data ? (
        <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
          <Pagination current={page} pageSize={9} total={kbs.data.total} onChange={setPage} showTotal={(t) => `共 ${t} 条`} />
        </div>
      ) : null}
    </div>
  );
}
