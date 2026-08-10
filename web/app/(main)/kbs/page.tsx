"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { api } from "@/api-client";
import { Icon } from "@/components/icons";
import { Empty, ErrorState, Pagination, SkeletonRows, Tag } from "@/components/ui";
import { formatNumber, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

export default function KbsPage() {
  const router = useRouter();
  const [page, setPage] = useState(1);
  const [view, setView] = useState<"card" | "table">("card");
  const kbs = useAsync(() => api.listKbs({ page, size: 9 }), [page]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">知识库</h1>
          <p className="page-desc">按业务域组织知识资产，治理策略随库生效</p>
        </div>
        <div className="page-actions">
          <div className="seg">
            <button className={`seg-item${view === "card" ? " active" : ""}`} onClick={() => setView("card")}>卡片</button>
            <button className={`seg-item${view === "table" ? " active" : ""}`} onClick={() => setView("table")}>表格</button>
          </div>
          <Link href="/kbs/new" className="btn btn-primary">
            <Icon name="plus" size={15} /> 新建知识库
          </Link>
        </div>
      </div>

      {kbs.loading ? (
        <div className="card"><SkeletonRows rows={4} height={88} /></div>
      ) : kbs.error ? (
        <div className="card"><ErrorState message={kbs.error} onRetry={kbs.reload} /></div>
      ) : (kbs.data?.items.length ?? 0) === 0 ? (
        <div className="card">
          <Empty
            icon="📚"
            title="暂无知识库"
            desc="创建知识库后可上传文档或接入连接器"
            action={<Link href="/kbs/new" className="btn btn-primary">新建知识库</Link>}
          />
        </div>
      ) : view === "card" ? (
        <div className="grid grid-3">
          {kbs.data?.items.map((kb) => (
            <Link key={kb.id} href={`/kbs/${kb.id}`} className="card card-hover" style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 8 }}>
                <strong style={{ fontSize: 15 }}>{kb.name}</strong>
                <Tag color={kb.status === "ACTIVE" ? "success" : kb.status === "ARCHIVED" ? "" : "danger"}>
                  {kb.status === "ACTIVE" ? "运行中" : kb.status === "ARCHIVED" ? "已归档" : "删除中"}
                </Tag>
              </div>
              <p style={{ color: "var(--text-2)", fontSize: 13, flex: 1, overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical" }}>
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
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr><th>名称</th><th>可见性</th><th>文档/分块</th><th>我的角色</th><th>状态</th><th>更新时间</th></tr>
            </thead>
            <tbody>
              {kbs.data?.items.map((kb) => (
                <tr key={kb.id} className="clickable" onClick={() => router.push(`/kbs/${kb.id}`)}>
                  <td><strong>{kb.name}</strong></td>
                  <td>{kb.visibility === "PRIVATE" ? "私有" : "租户可见"}</td>
                  <td>{kb.documentCount} / {formatNumber(kb.chunkCount)}</td>
                  <td><Tag color={statusText("kbRole", kb.role)[1]}>{statusText("kbRole", kb.role)[0]}</Tag></td>
                  <td><Tag color={kb.status === "ACTIVE" ? "success" : ""}>{kb.status}</Tag></td>
                  <td>{formatRelative(kb.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {kbs.data ? <Pagination page={page} size={9} total={kbs.data.total} onChange={setPage} /> : null}
    </div>
  );
}
