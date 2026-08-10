"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useState } from "react";

import { api } from "@/api-client";
import { Icon } from "@/components/icons";
import { ConfirmModal, ErrorState, Loading, Modal, Tabs, Tag, useToast } from "@/components/ui";
import { formatDateTime, formatFileSize, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

type TabKey = "versions" | "metadata" | "permission";

/** mock 预览正文：真实实现为受控预览流（view_content 权限），无 download_original 不请求原始文件。 */
function mockExcerpt(title: string): string {
  return `《${title}》内容预览（mock 摘要）\n\n此处展示经服务端净化后的受控预览内容。真实实现中：\n· 预览流按 view_content 权限签发；\n· 无 download_original 权限时不提供原始文件下载；\n· 历史版本与已撤回内容打开时重新授权，失败给出明确原因。`;
}

export default function DocumentDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const docId = Number(params.id);
  const [tab, setTab] = useState<TabKey>("versions");
  const [previewOpen, setPreviewOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [favorite, setFavorite] = useState<boolean | null>(null);
  const [retrying, setRetrying] = useState(false);

  const doc = useAsync(() => api.getDocument(docId), [docId]);

  if (doc.loading) return <div className="card"><Loading /></div>;
  if (doc.error || !doc.data) {
    return (
      <div className="card">
        <ErrorState message={doc.error ?? "文档不存在"} onRetry={doc.reload} />
        <p style={{ textAlign: "center", fontSize: 12, color: "var(--text-3)" }}>
          也可能：文档已删除、已撤回，或你失去了访问权限
        </p>
      </div>
    );
  }

  const data = doc.data;
  const isFav = favorite ?? data.isFavorite;
  const canDownload = data.sensitivity !== "RESTRICTED"; // mock 权限推导；真实实现由策略结果返回
  const failed = data.ingestStatus === "FAILED" || data.ingestStatus === "BLOCKED";

  return (
    <div className="page">
      <div className="page-header">
        <div style={{ display: "flex", gap: 14, alignItems: "flex-start" }}>
          <span className="file-icon" style={{ width: 48, height: 48, fontSize: 13 }}>{data.fileExt}</span>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
              <h1 className="page-title">{data.title}</h1>
              <Tag color={statusText("ingest", data.ingestStatus)[1]}>{statusText("ingest", data.ingestStatus)[0]}</Tag>
              <Tag color={statusText("review", data.reviewStatus)[1]}>{statusText("review", data.reviewStatus)[0]}</Tag>
              <Tag color={statusText("sensitivity", data.sensitivity)[1]}>{statusText("sensitivity", data.sensitivity)[0]}</Tag>
            </div>
            <p className="page-desc">
              {data.fileName} · {formatFileSize(data.fileSize)} · 当前版本 v{data.versionNo} ·{" "}
              <Link href={`/kbs/${data.kbId}`} style={{ color: "var(--primary)" }}>{data.kbName}</Link>
            </p>
          </div>
        </div>
        <div className="page-actions">
          <button className="btn" onClick={() => { setFavorite(!isFav); toast("success", isFav ? "已取消收藏" : "已收藏"); }}>
            <Icon name="star" size={15} /> {isFav ? "已收藏" : "收藏"}
          </button>
          <button className="btn" onClick={() => setPreviewOpen(true)}><Icon name="eye" size={15} /> 预览</button>
          {canDownload ? (
            <button className="btn" onClick={() => toast("info", "mock：原始文件流下载（需 download_original）")}><Icon name="download" size={15} /> 下载</button>
          ) : (
            <button className="btn" disabled title="无 download_original 权限"><Icon name="download" size={15} /> 下载</button>
          )}
          {failed ? (
            <button
              className="btn btn-primary"
              disabled={retrying}
              onClick={() => {
                setRetrying(true);
                setTimeout(() => { setRetrying(false); toast("success", "已重新入队摄取任务（mock）"); }, 600);
              }}
            >
              <Icon name="refresh" size={15} /> {retrying ? "提交中…" : "重试摄取"}
            </button>
          ) : (
            <button className="btn btn-danger" onClick={() => setDeleteOpen(true)}>删除</button>
          )}
        </div>
      </div>

      {failed ? (
        <div className="card" style={{ borderColor: "var(--danger)", background: "var(--danger-soft)" }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <Icon name="alert" size={18} />
            <div>
              <strong>摄取{data.ingestStatus === "BLOCKED" ? "被安全策略阻断" : "失败"}</strong>
              <div style={{ fontSize: 13, color: "var(--text-2)" }}>
                失败阶段：{data.ingestStatus === "BLOCKED" ? "内容安全扫描（DLP/恶意软件）" : "解析"} · 错误码 mock-DOC-4{data.id} · 可重试或联系管理员
              </div>
            </div>
          </div>
        </div>
      ) : null}

      <div className="grid grid-23">
        <div>
          <Tabs<TabKey>
            active={tab}
            onChange={setTab}
            items={[
              { key: "versions", label: `版本 (${data.versions.length})` },
              { key: "metadata", label: "元数据" },
              { key: "permission", label: "权限与血缘" },
            ]}
          />

          {tab === "versions" ? (
            <div className="table-wrap">
              <table className="table">
                <thead><tr><th>版本</th><th>大小</th><th>摄取状态</th><th>安全扫描</th><th>分块</th><th>创建</th></tr></thead>
                <tbody>
                  {data.versions.map((v) => (
                    <tr key={v.versionNo}>
                      <td>
                        v{v.versionNo}
                        {v.versionNo === data.versionNo ? <Tag color="primary">当前</Tag> : null}
                      </td>
                      <td>{formatFileSize(v.fileSize)}</td>
                      <td><Tag color={statusText("ingest", v.ingestStatus)[1]}>{statusText("ingest", v.ingestStatus)[0]}</Tag></td>
                      <td>
                        <Tag color={v.safetyStatus === "PASSED" ? "success" : v.safetyStatus === "BLOCKED" || v.safetyStatus === "FAILED" ? "danger" : "warning"}>
                          {v.safetyStatus === "PASSED" ? "通过" : v.safetyStatus === "PENDING" ? "待扫描" : v.safetyStatus === "BLOCKED" ? "阻断" : "失败"}
                        </Tag>
                      </td>
                      <td>{v.chunkCount}</td>
                      <td>{v.createdBy} · {formatDateTime(v.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}

          {tab === "metadata" ? (
            <div className="card">
              <dl className="kv">
                <dt>所有者</dt><dd>{data.ownerName}</dd>
                <dt>来源类型</dt><dd>{data.sourceType === "UPLOAD" ? "手工上传" : data.sourceType === "CONNECTOR" ? "连接器同步" : "网页采集"}</dd>
                <dt>标签</dt>
                <dd>{data.tags.length ? data.tags.map((t) => <Tag key={t} color="primary">{t}</Tag>) : "—"}</dd>
                <dt>语言</dt><dd>中文（自动识别，mock）</dd>
                <dt>复审日期</dt><dd>2026-12-31（到期将进入复审队列，mock）</dd>
              </dl>
              <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 12 }}>
                元数据表单由租户 schema 驱动（必填/枚举/日期/多值），契约冻结后实现编辑与批量预览。
              </p>
            </div>
          ) : null}

          {tab === "permission" ? (
            <div className="card">
              <h4 style={{ marginBottom: 10 }}>有效权限推导（可解释权限，mock）</h4>
              <dl className="kv">
                <dt>租户角色</dt><dd>成员 → 可访问租户内可见库</dd>
                <dt>知识库角色</dt><dd>EDITOR → 可编辑文档与元数据</dd>
                <dt>文档 ACL</dt><dd>无单独限制 → 继承知识库策略</dd>
                <dt>下载权限</dt><dd>{canDownload ? "允许（download_original）" : "拒绝：敏感级为绝密"}</dd>
              </dl>
              <h4 style={{ margin: "18px 0 10px" }}>来源血缘</h4>
              <dl className="kv">
                <dt>来源</dt><dd>{data.sourceType === "CONNECTOR" ? "连接器 ext-" + data.id : "浏览器上传"}</dd>
                <dt>内容指纹</dt><dd>sha256:…{String(data.id).padStart(6, "0")}（mock）</dd>
                <dt>最近更新</dt><dd>{formatRelative(data.updatedAt)}</dd>
              </dl>
            </div>
          ) : null}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div className="card">
            <h3 className="card-title">基本信息</h3>
            <dl className="kv" style={{ gridTemplateColumns: "88px 1fr", fontSize: 13 }}>
              <dt>文档 ID</dt><dd>{data.id}</dd>
              <dt>类型</dt><dd>{data.mimeType}</dd>
              <dt>版本</dt><dd>v{data.versionNo}（共 {data.versions.length} 版）</dd>
              <dt>分块数</dt><dd>{data.chunkCount}</dd>
              <dt>所有者</dt><dd>{data.ownerName}</dd>
              <dt>更新</dt><dd>{formatRelative(data.updatedAt)}</dd>
            </dl>
          </div>
          <div className="card">
            <h3 className="card-title">检索可见性</h3>
            <p style={{ fontSize: 13, color: "var(--text-2)" }}>
              {data.reviewStatus === "PUBLISHED" && data.ingestStatus === "READY"
                ? "当前版本已发布并就绪，可在授权范围内被搜索与问答引用。"
                : "当前文档未同时满足「已发布 + 摄取就绪」，不会进入在线索引别名。"}
            </p>
          </div>
        </div>
      </div>

      <Modal title={`预览 · ${data.fileName}`} open={previewOpen} onClose={() => setPreviewOpen(false)} large>
        <pre style={{ whiteSpace: "pre-wrap", fontFamily: "inherit", margin: 0, color: "var(--text-2)" }}>{mockExcerpt(data.title)}</pre>
      </Modal>

      <ConfirmModal
        open={deleteOpen}
        title="删除文档"
        danger
        loading={deleting}
        confirmText="确认删除"
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => {
          setDeleting(true);
          setTimeout(() => {
            setDeleting(false);
            setDeleteOpen(false);
            toast("success", "删除任务已提交，完成后生成删除证明（mock）");
            router.push("/documents");
          }, 700);
        }}
        description={
          <div>
            将删除「<strong>{data.title}</strong>」：立即从授权集合移除，随后异步清理对象存储、索引与缓存，并生成删除证明。
            受法律保全约束的内容无法删除。
          </div>
        }
      />
    </div>
  );
}
