"use client";

import { useRouter } from "next/navigation";
import { Button, Card, Pagination, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { StarOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { FavoriteItem } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { formatRelative } from "@/lib/format";
import { useAsync } from "@/lib/use-async";
import { useState } from "react";

const columns: TableColumnsType<FavoriteItem> = [
  {
    title: "文档",
    key: "title",
    render: (_, item) => (
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <StarOutlined style={{ color: "var(--warning)" }} />
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 500 }}>{item.title}</div>
          <div style={{ fontSize: 12, color: "var(--text-3)" }}>{item.fileName}</div>
        </div>
      </div>
    ),
  },
  { title: "知识库", dataIndex: "kbName" },
  {
    title: "收藏时间",
    dataIndex: "savedAt",
    width: 140,
    render: (v: string) => formatRelative(v),
  },
  {
    title: "操作",
    key: "action",
    width: 100,
    render: () => <Tag color="blue">查看</Tag>,
  },
];

export default function FavoritesPage() {
  const router = useRouter();
  const [page, setPage] = useState(1);
  const favorites = useAsync(() => api.listFavorites({ page, size: 10 }), [page]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">我的收藏</h1>
          <p className="page-desc">你收藏的文档,按收藏时间倒序排列</p>
        </div>
      </div>

      {favorites.error ? (
        <Card>
          <ErrorState message={favorites.error} onRetry={favorites.reload} />
        </Card>
      ) : favorites.loading ? (
        <Card>
          <SkeletonRows rows={5} />
        </Card>
      ) : (favorites.data?.items.length ?? 0) === 0 ? (
        <Card>
          <Empty
            icon="⭐"
            title="暂无收藏"
            desc="在文档详情页点击收藏,常用文档会出现在这里"
            action={<Button type="primary" onClick={() => router.push("/documents")}>浏览文档</Button>}
          />
        </Card>
      ) : (
        <Card>
          <Table<FavoriteItem>
            rowKey="documentId"
            columns={columns}
            dataSource={favorites.data?.items ?? []}
            pagination={false}
            onRow={(item) => ({
              onClick: () => router.push(`/documents/${item.documentId}`),
            })}
          />
          {favorites.data ? (
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
              <Pagination
                current={page}
                pageSize={10}
                total={favorites.data.total}
                onChange={setPage}
                showTotal={(t) => `共 ${t} 条`}
              />
            </div>
          ) : null}
        </Card>
      )}
    </div>
  );
}
