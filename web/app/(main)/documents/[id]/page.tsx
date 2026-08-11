"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Alert, Button, Descriptions, Modal, Space, Tabs, Tag, Tooltip } from "antd";
import { DownloadOutlined, EyeOutlined, ReloadOutlined, StarOutlined, DeleteOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { DocumentDetail } from "@/api-client";
import { AclEditor } from "@/components/document/acl-editor";
import { PreviewDialog } from "@/components/document/preview-dialog";
import { VersionHistory } from "@/components/document/version-history";
import { ErrorState, Loading } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { LineageGraph, type LineageEdge, type LineageNode } from "@/components/lineage-graph";
import { useTheme } from "@/components/theme-provider";
import { formatFileSize, formatRelative, statusText } from "@/lib/format";
import { resolveMode } from "@/lib/theme";
import { useAsync } from "@/lib/use-async";

type TabKey = "versions" | "metadata" | "permission";

/** 由文档详情推导 mock 血缘图：来源 → 文档 → 分块 → 索引 → 消费。 */
function buildLineage(doc: DocumentDetail): { nodes: LineageNode[]; edges: LineageEdge[] } {
  const sourceLabel = doc.sourceType === "CONNECTOR" ? `连接器 ext-${doc.id}` : doc.sourceType === "WEB" ? "网页采集" : "浏览器上传";
  const nodes: LineageNode[] = [
    { id: "source", label: sourceLabel, kind: "source" },
    { id: "doc", label: doc.fileName, kind: "document" },
    { id: "index", label: "向量索引 standard-1024", kind: "index" },
    { id: "consumer", label: "在线问答 / 全文搜索", kind: "consumer" },
  ];
  const edges: LineageEdge[] = [{ source: "source", target: "doc", label: doc.sourceType === "UPLOAD" ? "上传" : "同步" }];

  const chunkCount = Math.max(1, Math.min(doc.chunkCount || 3, 4));
  for (let i = 0; i < chunkCount; i++) {
    const cid = `chunk-${i}`;
    nodes.push({ id: cid, label: `分块 #${i + 1}`, kind: "chunk" });
    edges.push({ source: "doc", target: cid, label: "解析" });
    edges.push({ source: cid, target: "index", label: "向量化" });
  }
  edges.push({ source: "index", target: "consumer", label: "检索引用" });
  return { nodes, edges };
}
export default function DocumentDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const toast = useToast();
  const { config: themeConfig } = useTheme();
  const docId = Number(params.id);
  const [tab, setTab] = useState<TabKey>("versions");
  const [previewOpen, setPreviewOpen] = useState(false);
  const [targetPage, setTargetPage] = useState<number | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [favorite, setFavorite] = useState<boolean | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [rollingBack, setRollingBack] = useState(false);

  const handleRollback = async (versionNo: number) => {
    setRollingBack(true);
    try {
      await api.rollbackVersion(docId, versionNo);
      toast("success", "已回滚到该版本，重新进入摄取与发布流程");
      doc.reload();
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "回滚失败");
    } finally {
      setRollingBack(false);
    }
  };

  const doc = useAsync(() => api.getDocument(docId), [docId]);

  // 从搜索/问答引用携带 ?page= 直达预览并定位页码（组件内读 URL，避免 Suspense 约束）
  useEffect(() => {
    const pageParam = new URLSearchParams(window.location.search).get("page");
    const page = pageParam ? Number(pageParam) : null;
    if (page && !Number.isNaN(page)) {
      setTargetPage(page);
      setPreviewOpen(true);
    }
  }, [docId]);

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
  const retryLocked = (data.retryCount ?? 0) >= 3; // 解析失败重试上限 3 次（F2.2-4.2.5）
  const lineage = buildLineage(data);

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
          <Button
            icon={<StarOutlined />}
            onClick={async () => {
              try {
                const next = await api.toggleFavorite(docId);
                setFavorite(next);
                toast("success", next ? "已收藏" : "已取消收藏");
              } catch (err: unknown) {
                toast("error", err instanceof Error ? err.message : "操作失败");
              }
            }}
          >
            {isFav ? "已收藏" : "收藏"}
          </Button>
          <Button icon={<EyeOutlined />} onClick={() => setPreviewOpen(true)}>预览</Button>
          <Button
            icon={<DownloadOutlined />}
            disabled={!canDownload}
            title={canDownload ? undefined : "无 download_original 权限"}
            onClick={() => toast("info", "mock：原始文件流下载（需 download_original）")}
          >
            下载
          </Button>
          {failed ? (
            <Tooltip title={retryLocked ? "解析失败已重试 3 次，需管理员介入或重新上传" : undefined}>
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                loading={retrying}
                disabled={retryLocked}
                onClick={async () => {
                  setRetrying(true);
                  try {
                    await api.retryIngest(docId);
                    toast("success", "已重新入队摄取任务");
                    doc.reload();
                  } catch (err: unknown) {
                    toast("error", err instanceof Error ? err.message : "重试失败");
                  } finally {
                    setRetrying(false);
                  }
                }}
              >
                {retryLocked ? "已锁定（重试超限）" : "重试摄取"}
              </Button>
            </Tooltip>
          ) : (
            <Button danger icon={<DeleteOutlined />} onClick={() => setDeleteOpen(true)}>删除</Button>
          )}
        </div>
      </div>

      {failed ? (
        <Alert
          type="error"
          showIcon
          message={data.ingestStatus === "BLOCKED" ? "摄取被安全策略阻断" : "摄取失败"}
          description={`失败阶段：${data.ingestStatus === "BLOCKED" ? "内容安全扫描（DLP/恶意软件）" : "解析"} · 错误码 mock-DOC-4${data.id} · 可重试或联系管理员`}
          style={{ marginBottom: 16 }}
        />
      ) : null}

      <div className="grid grid-23">
        <div>
          <Tabs
            activeKey={tab}
            onChange={(key) => setTab(key as TabKey)}
            items={[
              { key: "versions", label: `版本 (${data.versions.length})` },
              { key: "metadata", label: "元数据" },
              { key: "permission", label: "权限与血缘" },
            ]}
          />

          {tab === "versions" ? (
            <VersionHistory
              versions={data.versions}
              currentVersionNo={data.versionNo}
              rollingBack={rollingBack}
              onRollback={(v) => void handleRollback(v)}
            />
          ) : null}

          {tab === "metadata" ? (
            <div className="card">
              <Descriptions
                size="small"
                column={1}
                items={[
                  { key: "owner", label: "所有者", children: data.ownerName },
                  {
                    key: "sourceType",
                    label: "来源类型",
                    children: data.sourceType === "UPLOAD" ? "手工上传" : data.sourceType === "CONNECTOR" ? "连接器同步" : "网页采集",
                  },
                  { key: "tags", label: "标签", children: data.tags.length ? <Space size={4} wrap>{data.tags.map((t) => <Tag key={t} color="blue">{t}</Tag>)}</Space> : "—" },
                  { key: "lang", label: "语言", children: "中文（自动识别，mock）" },
                  { key: "reviewDate", label: "复审日期", children: "2026-12-31（到期将进入复审队列，mock）" },
                ]}
              />
              <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 12 }}>
                元数据表单由租户 schema 驱动（必填/枚举/日期/多值），契约冻结后实现编辑与批量预览。
              </p>
            </div>
          ) : null}

          {tab === "permission" ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
              <AclEditor documentId={data.id} kbId={data.kbId} />
              <div className="card">
                <h4 style={{ margin: "0 0 10px" }}>来源血缘</h4>
                <LineageGraph nodes={lineage.nodes} edges={lineage.edges} dark={resolveMode(themeConfig.mode) === "dark"} />
              </div>
            </div>
          ) : null}
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div className="card">
            <h3 className="card-title">基本信息</h3>
            <Descriptions
              size="small"
              column={1}
              items={[
                { key: "id", label: "文档 ID", children: data.id },
                { key: "mime", label: "类型", children: data.mimeType },
                { key: "version", label: "版本", children: `v${data.versionNo}（共 ${data.versions.length} 版）` },
                { key: "chunks", label: "分块数", children: data.chunkCount },
                { key: "owner", label: "所有者", children: data.ownerName },
                { key: "updated", label: "更新", children: formatRelative(data.updatedAt) },
              ]}
            />
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

      <PreviewDialog open={previewOpen} onClose={() => setPreviewOpen(false)} doc={data} targetPage={targetPage} />

      <Modal
        title="删除文档"
        open={deleteOpen}
        okText="确认删除"
        cancelText="取消"
        confirmLoading={deleting}
        okButtonProps={{ danger: true }}
        onCancel={() => setDeleteOpen(false)}
        onOk={async () => {
          setDeleting(true);
          try {
            await api.deleteDocument(docId);
            toast("success", "删除已完成并生成删除证明");
            router.push("/documents");
          } catch (err: unknown) {
            toast("error", err instanceof Error ? err.message : "删除失败");
            setDeleting(false);
            setDeleteOpen(false);
          }
        }}
      >
        <div>
          将删除「<strong>{data.title}</strong>」：立即从授权集合移除，随后异步清理对象存储、索引与缓存，并生成删除证明。
          受法律保全约束的内容无法删除。
        </div>
      </Modal>
    </div>
  );
}
