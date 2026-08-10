"use client";

import { useState } from "react";

import { api } from "@/api-client";
import type { ApiKey } from "@/api-client";
import { Icon } from "@/components/icons";
import { ConfirmModal, Empty, ErrorState, Modal, SkeletonRows, Tag, useToast } from "@/components/ui";
import { formatDateTime, formatRelative } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const SCOPE_OPTIONS = ["search:read", "chat:write", "docs:read", "docs:write", "analytics:read"];

export default function ApiKeysPage() {
  const toast = useToast();
  const keys = useAsync(() => api.listApiKeys());
  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));

  const [createOpen, setCreateOpen] = useState(false);
  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<Set<string>>(new Set(["search:read"]));
  const [kbScope, setKbScope] = useState("");
  const [expireDays, setExpireDays] = useState("90");
  const [creating, setCreating] = useState(false);
  const [newSecret, setNewSecret] = useState<string | null>(null);
  const [createdKeys, setCreatedKeys] = useState<ApiKey[]>([]);
  const [revokedIds, setRevokedIds] = useState<Set<number>>(new Set());
  const [revokeTarget, setRevokeTarget] = useState<ApiKey | null>(null);
  const [revoking, setRevoking] = useState(false);

  const toggleScope = (scope: string) => {
    setScopes((prev) => {
      const next = new Set(prev);
      if (next.has(scope)) next.delete(scope);
      else next.add(scope);
      return next;
    });
  };

  const create = () => {
    if (!name.trim()) {
      toast("error", "请输入 Key 名称");
      return;
    }
    if (scopes.size === 0) {
      toast("error", "至少选择一个 scope");
      return;
    }
    setCreating(true);
    setTimeout(() => {
      const id = Date.now();
      const key: ApiKey = {
        id,
        name: name.trim(),
        keyPrefix: `rk-${String(id).slice(-6)}`,
        scopes: [...scopes],
        kbIds: kbScope ? [Number(kbScope)] : [],
        status: "ACTIVE",
        expiresAt: new Date(Date.now() + Number(expireDays) * 86400000).toISOString(),
        lastUsedAt: null,
        createdAt: new Date().toISOString(),
      };
      setCreatedKeys((prev) => [key, ...prev]);
      setNewSecret(`rk-${String(id).slice(-6)}-${Math.random().toString(36).slice(2, 18)}`);
      setCreating(false);
      setCreateOpen(false);
      setName("");
    }, 600);
  };

  const copySecret = async () => {
    if (!newSecret) return;
    try {
      await navigator.clipboard.writeText(newSecret);
      toast("success", "已复制到剪贴板");
    } catch {
      toast("info", "请手动选择复制");
    }
  };

  const allKeys = [...createdKeys, ...(keys.data ?? [])];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · API Key</h1>
          <p className="page-desc">机器访问必须有 scope、知识库范围与有效期；明文只展示一次</p>
        </div>
        <div className="page-actions">
          <button className="btn btn-primary" onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={15} /> 创建 API Key
          </button>
        </div>
      </div>

      {keys.loading ? (
        <div className="card"><SkeletonRows rows={4} /></div>
      ) : keys.error ? (
        <div className="card"><ErrorState message={keys.error} onRetry={keys.reload} /></div>
      ) : allKeys.length === 0 ? (
        <div className="card"><Empty icon="🔑" title="暂无 API Key" desc="为外部系统创建受限的机器访问凭证" action={<button className="btn btn-primary" onClick={() => setCreateOpen(true)}>创建 API Key</button>} /></div>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>名称</th><th>前缀</th><th>Scope</th><th>知识库范围</th><th>状态</th><th>最近使用</th><th>过期时间</th><th>操作</th></tr></thead>
            <tbody>
              {allKeys.map((key) => {
                const revoked = revokedIds.has(key.id) || key.status === "REVOKED";
                return (
                  <tr key={key.id}>
                    <td style={{ fontWeight: 500 }}>{key.name}</td>
                    <td><code style={{ fontSize: 12 }}>{key.keyPrefix}…</code></td>
                    <td>
                      <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                        {key.scopes.map((s) => <Tag key={s} color="info">{s}</Tag>)}
                      </div>
                    </td>
                    <td>{key.kbIds.length ? `${key.kbIds.length} 个库` : "按授权"}</td>
                    <td><Tag color={revoked ? "danger" : key.status === "EXPIRED" ? "warning" : "success"}>{revoked ? "已吊销" : key.status === "EXPIRED" ? "已过期" : "有效"}</Tag></td>
                    <td>{key.lastUsedAt ? formatRelative(key.lastUsedAt) : "从未使用"}</td>
                    <td>{key.expiresAt ? formatDateTime(key.expiresAt).slice(0, 10) : "永不过期"}</td>
                    <td>
                      {!revoked && key.status !== "EXPIRED" ? (
                        <button className="btn btn-sm btn-ghost" style={{ color: "var(--danger)" }} onClick={() => setRevokeTarget(key)}>吊销</button>
                      ) : "—"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        title="创建 API Key"
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <button className="btn" onClick={() => setCreateOpen(false)} disabled={creating}>取消</button>
            <button className="btn btn-primary" onClick={create} disabled={creating}>{creating ? "创建中…" : "创建"}</button>
          </>
        }
      >
        <div className="field">
          <label className="field-label">名称<span className="req">*</span></label>
          <input className="input" placeholder="例如：BI 报表同步" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="field">
          <label className="field-label">Scope<span className="req">*</span></label>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {SCOPE_OPTIONS.map((scope) => (
              <button key={scope} className={`chat-suggestion${scopes.has(scope) ? "" : ""}`} style={scopes.has(scope) ? { borderColor: "var(--primary)", color: "var(--primary)", borderStyle: "solid" } : undefined} onClick={() => toggleScope(scope)}>
                {scope}
              </button>
            ))}
          </div>
        </div>
        <div className="field">
          <label className="field-label">知识库范围</label>
          <select className="select" value={kbScope} onChange={(e) => setKbScope(e.target.value)}>
            <option value="">按调用者授权（推荐）</option>
            {kbs.data?.items.map((kb) => <option key={kb.id} value={kb.id}>{kb.name}</option>)}
          </select>
          <div className="field-hint">机器凭证无法访问未授权知识库，即使 scope 允许</div>
        </div>
        <div className="field" style={{ marginBottom: 0 }}>
          <label className="field-label">有效期</label>
          <select className="select" value={expireDays} onChange={(e) => setExpireDays(e.target.value)}>
            <option value="30">30 天</option>
            <option value="90">90 天</option>
            <option value="180">180 天</option>
            <option value="365">1 年</option>
          </select>
        </div>
      </Modal>

      <Modal title="API Key 创建成功" open={newSecret !== null} onClose={() => setNewSecret(null)}
        footer={<button className="btn btn-primary" onClick={() => setNewSecret(null)}>我已妥善保存</button>}
      >
        <p style={{ color: "var(--danger)", marginBottom: 12, fontSize: 13 }}>
          明文仅展示这一次，关闭后无法再次查看。服务端只保存不可逆摘要。
        </p>
        <div style={{ display: "flex", gap: 8 }}>
          <input className="input" readOnly value={newSecret ?? ""} onFocus={(e) => e.target.select()} />
          <button className="btn" onClick={() => void copySecret()}><Icon name="copy" size={15} /> 复制</button>
        </div>
      </Modal>

      <ConfirmModal
        open={revokeTarget !== null}
        title="吊销 API Key"
        danger
        loading={revoking}
        confirmText="确认吊销"
        onClose={() => setRevokeTarget(null)}
        onConfirm={() => {
          setRevoking(true);
          setTimeout(() => {
            setRevokedIds((prev) => new Set(prev).add(revokeTarget!.id));
            setRevoking(false);
            toast("success", `「${revokeTarget?.name}」已吊销，立即生效（mock）`);
            setRevokeTarget(null);
          }, 600);
        }}
        description={
          <div>
            吊销「<strong>{revokeTarget?.name}</strong>」（{revokeTarget?.keyPrefix}…）后，使用该凭证的调用将立即被拒绝。此操作不可撤销。
          </div>
        }
      />
    </div>
  );
}
