"use client";

import { useState } from "react";

import { api } from "@/api-client";
import { Icon } from "@/components/icons";
import { ConfirmModal, Empty, ErrorState, Pagination, SkeletonRows, Tabs, Tag, useToast } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

type TabKey = "users" | "orgs";

export default function AdminUsersPage() {
  const toast = useToast();
  const [tab, setTab] = useState<TabKey>("users");
  const [page, setPage] = useState(1);
  const [disableTarget, setDisableTarget] = useState<{ id: number; name: string } | null>(null);
  const [disabling, setDisabling] = useState(false);
  const [disabledIds, setDisabledIds] = useState<Set<number>>(new Set());

  const users = useAsync(() => api.listUsers({ page, size: 10 }), [page]);
  const orgs = useAsync(() => api.listOrgs(), [tab === "orgs"]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · 成员与组织</h1>
          <p className="page-desc">全局用户 + 租户成员分离；停用成员在 SLA 内失去访问</p>
        </div>
        <div className="page-actions">
          <button className="btn btn-primary" onClick={() => toast("info", "mock：邀请成员（SCIM 同步为 P1 能力）")}>
            <Icon name="plus" size={15} /> 邀请成员
          </button>
        </div>
      </div>

      <Tabs<TabKey> active={tab} onChange={setTab} items={[{ key: "users", label: "成员" }, { key: "orgs", label: "组织架构" }]} />

      {tab === "users" ? (
        users.loading ? (
          <div className="card"><SkeletonRows rows={6} /></div>
        ) : users.error ? (
          <div className="card"><ErrorState message={users.error} onRetry={users.reload} /></div>
        ) : (
          <>
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>成员</th><th>组织</th><th>角色</th><th>状态</th><th>最近登录</th><th>操作</th></tr></thead>
                <tbody>
                  {users.data?.items.map((u) => {
                    const disabled = disabledIds.has(u.id) || u.status === "DISABLED";
                    return (
                      <tr key={u.id}>
                        <td>
                          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                            <span className="avatar">{u.name.slice(0, 1)}</span>
                            <div>
                              <div style={{ fontWeight: 500 }}>{u.name}</div>
                              <div style={{ fontSize: 12, color: "var(--text-3)" }}>{u.email}</div>
                            </div>
                          </div>
                        </td>
                        <td>{u.orgName}</td>
                        <td><Tag color="primary">{u.role}</Tag></td>
                        <td><Tag color={disabled ? "danger" : "success"}>{disabled ? "已停用" : "正常"}</Tag></td>
                        <td>{formatDateTime(u.lastLoginAt)}</td>
                        <td>
                          {!disabled ? (
                            <button className="btn btn-sm btn-ghost" style={{ color: "var(--danger)" }} onClick={() => setDisableTarget({ id: u.id, name: u.name })}>
                              停用
                            </button>
                          ) : (
                            <button className="btn btn-sm btn-ghost" onClick={() => { setDisabledIds((prev) => { const n = new Set(prev); n.delete(u.id); return n; }); toast("success", `已恢复 ${u.name}（mock）`); }}>
                              恢复
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            {users.data ? <Pagination page={page} size={10} total={users.data.total} onChange={setPage} /> : null}
          </>
        )
      ) : orgs.loading ? (
        <div className="card"><SkeletonRows rows={5} /></div>
      ) : orgs.error ? (
        <div className="card"><ErrorState message={orgs.error} onRetry={orgs.reload} /></div>
      ) : (orgs.data?.length ?? 0) === 0 ? (
        <div className="card"><Empty icon="🏢" title="暂无组织" /></div>
      ) : (
        <div className="card">
          {(orgs.data ?? []).map((org) => (
            <div key={org.id} className="list-row" style={{ paddingLeft: org.parentId ? 28 : 0 }}>
              <Icon name="building" size={16} />
              <span className="list-row-main">
                <div className="list-row-title">{org.name}</div>
                <div className="list-row-sub">{org.path}</div>
              </span>
              <span style={{ color: "var(--text-2)", fontSize: 13 }}>{org.memberCount} 人</span>
              <Tag color={org.status === "ACTIVE" ? "success" : "danger"}>{org.status === "ACTIVE" ? "正常" : "已停用"}</Tag>
            </div>
          ))}
        </div>
      )}

      <ConfirmModal
        open={disableTarget !== null}
        title="停用成员"
        danger
        loading={disabling}
        confirmText="确认停用"
        onClose={() => setDisableTarget(null)}
        onConfirm={() => {
          setDisabling(true);
          setTimeout(() => {
            setDisabledIds((prev) => new Set(prev).add(disableTarget!.id));
            setDisabling(false);
            toast("success", `已停用 ${disableTarget?.name}，其会话与 API 访问将在目标 SLA 内失效（mock）`);
            setDisableTarget(null);
          }, 600);
        }}
        description={
          <div>
            停用「<strong>{disableTarget?.name}</strong>」后：其登录态、API Key 调用与知识库访问将被回收；所属文档的所有者需另行交接。
          </div>
        }
      />
    </div>
  );
}
