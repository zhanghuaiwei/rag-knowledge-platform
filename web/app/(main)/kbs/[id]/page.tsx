"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";

import { api } from "@/api-client";
import type { Kb } from "@/api-client";
import { Icon } from "@/components/icons";
import { ConfirmModal, Empty, ErrorState, Loading, SkeletonRows, Switch, Tabs, Tag, useToast } from "@/components/ui";
import { formatDate, formatDateTime, formatNumber, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

type TabKey = "overview" | "documents" | "members" | "connectors" | "settings";

/** 基本设置：受控表单，保存走 updateKb 契约（mock 下直接写内存库）。 */
function KbSettingsForm({ kb, canEdit, onSaved }: { kb: Kb; canEdit: boolean; onSaved: () => void }) {
  const toast = useToast();
  const [name, setName] = useState(kb.name);
  const [description, setDescription] = useState(kb.description);
  const [requiresReview, setRequiresReview] = useState(kb.requiresReview);
  const [saving, setSaving] = useState(false);
  const [nameError, setNameError] = useState("");

  const dirty = name !== kb.name || description !== kb.description || requiresReview !== kb.requiresReview;

  const save = async () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setNameError("名称不能为空");
      return;
    }
    if (trimmed.length > 40) {
      setNameError("名称不超过 40 字");
      return;
    }
    setSaving(true);
    try {
      await api.updateKb(kb.id, { name: trimmed, description: description.trim(), requiresReview });
      toast("success", "设置已保存");
      onSaved();
    } catch (err) {
      toast("error", err instanceof Error ? err.message : "保存失败，请重试");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <h3 className="card-title">基本设置</h3>
      <div className="field">
        <label className="field-label">
          名称<span className="req">*</span>
        </label>
        <input className="input" value={name} disabled={!canEdit} maxLength={40} onChange={(e) => { setName(e.target.value); setNameError(""); }} />
        {nameError ? <p className="field-error">{nameError}</p> : null}
      </div>
      <div className="field">
        <label className="field-label">描述</label>
        <textarea className="textarea" value={description} disabled={!canEdit} maxLength={200} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="setting-row">
        <div><div className="setting-label">发布前审核</div><div className="setting-desc">调整仅影响新提交内容</div></div>
        <Switch checked={requiresReview} disabled={!canEdit} onChange={setRequiresReview} />
      </div>
      <button className="btn btn-primary" style={{ marginTop: 12 }} disabled={!canEdit || !dirty || saving} onClick={() => void save()}>
        {saving ? "保存中…" : "保存设置"}
      </button>
    </div>
  );
}

export default function KbDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const kbId = Number(params.id);
  const [tab, setTab] = useState<TabKey>("overview");
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [archiving, setArchiving] = useState(false);

  const kb = useAsync(() => api.getKb(kbId), [kbId]);
  const health = useAsync(() => api.getKnowledgeHealth(), [kbId]);
  const docs = useAsync(() => api.listDocuments({ kbId, page: 1, size: 8 }), [kbId, tab === "documents"]);
  const members = useAsync(() => api.listKbMembers(kbId), [kbId, tab === "members"]);
  const connectors = useAsync(() => api.listConnectors(), [kbId, tab === "connectors"]);

  if (kb.loading) return <div className="card"><Loading /></div>;
  if (kb.error || !kb.data) return <div className="card"><ErrorState message={kb.error ?? "知识库不存在"} onRetry={kb.reload} /></div>;

  const data = kb.data;
  const isOwner = data.role === "OWNER";

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <h1 className="page-title">{data.name}</h1>
            <Tag color={data.status === "ACTIVE" ? "success" : "danger"}>{data.status === "ACTIVE" ? "运行中" : data.status}</Tag>
            <Tag color={statusText("kbRole", data.role)[1]}>我的角色：{statusText("kbRole", data.role)[0]}</Tag>
          </div>
          <p className="page-desc">{data.description}</p>
        </div>
        <div className="page-actions">
          <Link href={`/chat?kb=${data.id}`} className="btn"><Icon name="chat" size={15} /> 基于此库问答</Link>
          {data.role !== "VIEWER" ? (
            <Link href={`/documents?kbId=${data.id}&upload=1`} className="btn btn-primary"><Icon name="upload" size={15} /> 上传文档</Link>
          ) : null}
        </div>
      </div>

      <Tabs<TabKey>
        active={tab}
        onChange={setTab}
        items={[
          { key: "overview", label: "概览" },
          { key: "documents", label: `文档 (${data.documentCount})` },
          { key: "members", label: `成员 (${data.members.length})` },
          { key: "connectors", label: "连接器" },
          { key: "settings", label: "设置" },
        ]}
      />

      {tab === "overview" ? (
        <>
          <div className="grid grid-4">
            <div className="card stat-card"><span className="stat-label"><Icon name="doc" size={15} /> 文档</span><span className="stat-value">{formatNumber(data.documentCount)}</span></div>
            <div className="card stat-card"><span className="stat-label"><Icon name="filter" size={15} /> 分块</span><span className="stat-value">{formatNumber(data.chunkCount)}</span></div>
            <div className="card stat-card">
              <span className="stat-label"><Icon name="alert" size={15} /> 无答案率</span>
              <span className="stat-value">{health.data ? `${(health.data.noAnswerRate * 100).toFixed(1)}%` : "…"}</span>
            </div>
            <div className="card stat-card">
              <span className="stat-label"><Icon name="clock" size={15} /> 新鲜度</span>
              <span className="stat-value">{health.data ? `${(health.data.freshnessScore * 100).toFixed(0)}%` : "…"}</span>
            </div>
          </div>
          <div className="card">
            <h3 className="card-title">库配置</h3>
            <dl className="kv">
              <dt>可见性</dt><dd>{data.visibility === "PRIVATE" ? "私有（仅成员）" : "租户内可见"}</dd>
              <dt>数据区域</dt><dd>{data.dataRegion}</dd>
              <dt>索引 Profile</dt><dd>{data.indexProfileName}（不可变；换模需重建索引并原子切换别名）</dd>
              <dt>发布审核</dt><dd>{data.requiresReview ? "开启：解析就绪后需审核发布" : "关闭"}</dd>
              <dt>OCR</dt><dd>{data.ocrEnabled ? "已启用" : "未启用"}</dd>
              <dt>创建时间</dt><dd>{formatDate(data.createdAt)}</dd>
            </dl>
          </div>
        </>
      ) : null}

      {tab === "documents" ? (
        docs.loading ? (
          <div className="card"><SkeletonRows rows={5} /></div>
        ) : (docs.data?.items.length ?? 0) === 0 ? (
          <div className="card"><Empty icon="📄" title="暂无文档" desc="上传文档或接入连接器开始构建知识" /></div>
        ) : (
          <div className="table-wrap">
            <table className="table">
              <thead><tr><th>文档</th><th>摄取状态</th><th>审核</th><th>敏感级</th><th>版本</th><th>更新</th></tr></thead>
              <tbody>
                {docs.data?.items.map((doc) => (
                  <tr key={doc.id} className="clickable" onClick={() => router.push(`/documents/${doc.id}`)}>
                    <td>
                      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
                        <span className="file-icon">{doc.fileExt}</span>
                        <div>
                          <div style={{ fontWeight: 500 }}>{doc.title}</div>
                          <div style={{ fontSize: 12, color: "var(--text-3)" }}>{doc.fileName}</div>
                        </div>
                      </div>
                    </td>
                    <td><Tag color={statusText("ingest", doc.ingestStatus)[1]}>{statusText("ingest", doc.ingestStatus)[0]}</Tag></td>
                    <td><Tag color={statusText("review", doc.reviewStatus)[1]}>{statusText("review", doc.reviewStatus)[0]}</Tag></td>
                    <td><Tag color={statusText("sensitivity", doc.sensitivity)[1]}>{statusText("sensitivity", doc.sensitivity)[0]}</Tag></td>
                    <td>v{doc.versionNo}</td>
                    <td>{formatRelative(doc.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}

      {tab === "members" ? (
        members.loading ? (
          <div className="card"><SkeletonRows rows={4} /></div>
        ) : members.error ? (
          <div className="card"><ErrorState message={members.error} onRetry={members.reload} /></div>
        ) : (
          <div className="card">
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 12 }}>
              <h3 style={{ fontSize: 15 }}>成员与角色</h3>
              {isOwner ? <button className="btn btn-primary btn-sm" onClick={() => toast("info", "mock：邀请成员弹窗（契约待冻结）")}><Icon name="plus" size={13} /> 邀请成员</button> : null}
            </div>
            {(members.data ?? data.members).map((m) => (
              <div key={m.userId} className="list-row">
                <span className="avatar">{m.userName.slice(0, 1)}</span>
                <span className="list-row-main"><div className="list-row-title">{m.userName}</div></span>
                <Tag color={statusText("kbRole", m.role)[1]}>{statusText("kbRole", m.role)[0]}</Tag>
                {isOwner && m.role !== "OWNER" ? (
                  <button className="btn btn-sm btn-ghost" onClick={() => toast("info", "mock：移除成员需影响预览与二次确认")}>移除</button>
                ) : null}
              </div>
            ))}
          </div>
        )
      ) : null}

      {tab === "connectors" ? (
        connectors.loading ? (
          <div className="card"><SkeletonRows rows={3} /></div>
        ) : (
          <div className="grid grid-2">
            {(connectors.data ?? []).map((c) => (
              <div key={c.id} className="card">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                  <strong>{c.name}</strong>
                  <Tag color={c.status === "ACTIVE" ? "success" : c.status === "PAUSED" ? "warning" : "danger"}>
                    {c.status === "ACTIVE" ? "同步中" : c.status === "PAUSED" ? "已暂停" : "异常"}
                  </Tag>
                </div>
                <dl className="kv" style={{ gridTemplateColumns: "100px 1fr", fontSize: 13 }}>
                  <dt>提供方</dt><dd>{c.providerKey}</dd>
                  <dt>同步模式</dt><dd>{c.syncMode === "MANUAL" ? "手动" : c.syncMode === "SCHEDULED" ? "定时" : "Webhook"}</dd>
                  <dt>最近成功</dt><dd>{c.lastSuccessAt ? formatDateTime(c.lastSuccessAt) : "—"}</dd>
                  <dt>游标年龄</dt><dd>{c.cursorAgeMin} 分钟{c.cursorAgeMin > 240 ? "（超新鲜度 SLA）" : ""}</dd>
                  <dt>本轮同步</dt><dd>发现 {c.counts.discovered} · 新增 {c.counts.created} · 更新 {c.counts.updated} · 删除 {c.counts.deleted} · 失败 {c.counts.failed}</dd>
                </dl>
                {c.lastErrorCode ? <p style={{ color: "var(--danger)", fontSize: 12, marginTop: 8 }}>最近错误：{c.lastErrorCode}</p> : null}
                <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
                  <button className="btn btn-sm" onClick={() => toast("info", "mock：已触发一次手动同步")}><Icon name="refresh" size={13} /> 立即同步</button>
                  <button className="btn btn-sm btn-ghost" onClick={() => toast("info", "mock：同步任务详情与单对象重放（契约待冻结）")}>任务详情</button>
                </div>
              </div>
            ))}
            {(connectors.data?.length ?? 0) === 0 ? (
              <div className="card" style={{ gridColumn: "1 / -1" }}>
                <Empty icon="🔌" title="未接入连接器" desc="支持对象存储、SharePoint/OneDrive、Confluence 等来源（首批范围待评审）" />
              </div>
            ) : null}
          </div>
        )
      ) : null}

      {tab === "settings" ? (
        <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 720 }}>
          <KbSettingsForm key={data.id} kb={data} canEdit={data.role !== "VIEWER"} onSaved={() => kb.reload()} />

          <div className="card" style={{ borderColor: "var(--danger)" }}>
            <h3 className="card-title" style={{ color: "var(--danger)" }}>危险操作</h3>
            <div className="setting-row">
              <div>
                <div className="setting-label">归档知识库</div>
                <div className="setting-desc">归档后不可上传与问答，可随时恢复</div>
              </div>
              <button className="btn btn-danger btn-sm" disabled={!isOwner} title={isOwner ? undefined : "仅所有者可操作"} onClick={() => setArchiveOpen(true)}>
                归档
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <ConfirmModal
        open={archiveOpen}
        title="归档知识库"
        danger
        loading={archiving}
        confirmText="确认归档"
        onClose={() => setArchiveOpen(false)}
        onConfirm={() => {
          setArchiving(true);
          setTimeout(() => {
            setArchiving(false);
            setArchiveOpen(false);
            toast("success", "知识库已归档（mock）");
            router.push("/kbs");
          }, 600);
        }}
        description={
          <div>
            将归档「<strong>{data.name}</strong>」：{data.documentCount} 篇文档将不可检索与问答，{data.members.length} 名成员仅可查看。
            该操作可恢复，但传播期间按安全策略立即收紧访问。
          </div>
        }
      />
    </div>
  );
}
