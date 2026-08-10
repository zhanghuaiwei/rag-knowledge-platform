"use client";

import { useParams, useRouter } from "next/navigation";
import { useState } from "react";
import { Button, Card, Descriptions, List, Modal, Space, Table, Tabs, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { MessageOutlined, PlusOutlined, ReloadOutlined, UploadOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { Connector, DocumentSummary } from "@/api-client";
import { Empty, ErrorState, Loading, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { KbSettingsForm } from "@/components/kb-settings-form";
import { StatCard } from "@/components/stat-card";
import { formatDate, formatDateTime, formatNumber, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

type TabKey = "overview" | "documents" | "members" | "connectors" | "settings";

const docColumns: TableColumnsType<DocumentSummary> = [
  {
    title: "文档",
    key: "title",
    render: (_, doc) => (
      <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
        <span className="file-icon">{doc.fileExt}</span>
        <div>
          <div style={{ fontWeight: 500 }}>{doc.title}</div>
          <div style={{ fontSize: 12, color: "var(--text-3)" }}>{doc.fileName}</div>
        </div>
      </div>
    ),
  },
  {
    title: "摄取状态",
    dataIndex: "ingestStatus",
    width: 110,
    render: (v: DocumentSummary["ingestStatus"]) => {
      const [label, color] = statusText("ingest", v);
      return <Tag color={color}>{label}</Tag>;
    },
  },
  {
    title: "审核",
    dataIndex: "reviewStatus",
    width: 100,
    render: (v: DocumentSummary["reviewStatus"]) => {
      const [label, color] = statusText("review", v);
      return <Tag color={color}>{label}</Tag>;
    },
  },
  {
    title: "敏感级",
    dataIndex: "sensitivity",
    width: 90,
    render: (v: DocumentSummary["sensitivity"]) => {
      const [label, color] = statusText("sensitivity", v);
      return <Tag color={color}>{label}</Tag>;
    },
  },
  { title: "版本", dataIndex: "versionNo", width: 70, render: (v: number) => `v${v}` },
  { title: "更新", dataIndex: "updatedAt", width: 110, render: (v: string) => formatRelative(v) },
];

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
            <Tag color={data.status === "ACTIVE" ? "success" : "error"}>{data.status === "ACTIVE" ? "运行中" : data.status}</Tag>
            <Tag color={statusText("kbRole", data.role)[1]}>我的角色：{statusText("kbRole", data.role)[0]}</Tag>
          </div>
          <p className="page-desc">{data.description}</p>
        </div>
        <div className="page-actions">
          <Button icon={<MessageOutlined />} onClick={() => router.push(`/chat?kb=${data.id}`)}>基于此库问答</Button>
          {data.role !== "VIEWER" ? (
            <Button type="primary" icon={<UploadOutlined />} onClick={() => router.push(`/documents?kbId=${data.id}&upload=1`)}>
              上传文档
            </Button>
          ) : null}
        </div>
      </div>

      <Tabs
        activeKey={tab}
        onChange={(key) => setTab(key as TabKey)}
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
            <StatCard icon="doc" label="文档" value={formatNumber(data.documentCount)} />
            <StatCard icon="filter" label="分块" value={formatNumber(data.chunkCount)} />
            <StatCard icon="alert" label="无答案率" value={health.data ? `${(health.data.noAnswerRate * 100).toFixed(1)}%` : undefined} loading={health.loading} />
            <StatCard icon="clock" label="新鲜度" value={health.data ? `${(health.data.freshnessScore * 100).toFixed(0)}%` : undefined} loading={health.loading} />
          </div>
          <div className="card">
            <h3 className="card-title">库配置</h3>
            <Descriptions
              size="small"
              column={1}
              items={[
                { key: "visibility", label: "可见性", children: data.visibility === "PRIVATE" ? "私有（仅成员）" : "租户内可见" },
                { key: "region", label: "数据区域", children: data.dataRegion },
                { key: "profile", label: "索引 Profile", children: `${data.indexProfileName}（不可变；换模需重建索引并原子切换别名）` },
                { key: "review", label: "发布审核", children: data.requiresReview ? "开启：解析就绪后需审核发布" : "关闭" },
                { key: "ocr", label: "OCR", children: data.ocrEnabled ? "已启用" : "未启用" },
                { key: "created", label: "创建时间", children: formatDate(data.createdAt) },
              ]}
            />
          </div>
        </>
      ) : null}

      {tab === "documents" ? (
        <Card>
          <Table<DocumentSummary>
            rowKey="id"
            columns={docColumns}
            dataSource={docs.data?.items ?? []}
            loading={docs.loading}
            pagination={false}
            onRow={(doc) => ({ onClick: () => router.push(`/documents/${doc.id}`) })}
            locale={{ emptyText: <Empty icon="📄" title="暂无文档" desc="上传文档或接入连接器开始构建知识" /> }}
          />
        </Card>
      ) : null}

      {tab === "members" ? (
        members.loading ? (
          <Card><SkeletonRows rows={4} /></Card>
        ) : members.error ? (
          <Card><ErrorState message={members.error} onRetry={members.reload} /></Card>
        ) : (
          <Card
            title="成员与角色"
            extra={isOwner ? (
              <Button size="small" type="primary" icon={<PlusOutlined />} onClick={() => toast("info", "mock：邀请成员弹窗（契约待冻结）")}>
                邀请成员
              </Button>
            ) : null}
          >
            <List
              dataSource={members.data ?? data.members}
              renderItem={(m) => (
                <List.Item
                  actions={isOwner && m.role !== "OWNER" ? [
                    <Button key="remove" size="small" type="link" danger onClick={() => toast("info", "mock：移除成员需影响预览与二次确认")}>
                      移除
                    </Button>,
                  ] : []}
                >
                  <List.Item.Meta title={m.userName} />
                  <Tag color={statusText("kbRole", m.role)[1]}>{statusText("kbRole", m.role)[0]}</Tag>
                </List.Item>
              )}
            />
          </Card>
        )
      ) : null}

      {tab === "connectors" ? (
        connectors.loading ? (
          <Card><SkeletonRows rows={3} /></Card>
        ) : (
          <div className="grid grid-2">
            {(connectors.data ?? []).map((c: Connector) => (
              <Card key={c.id}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                  <strong>{c.name}</strong>
                  <Tag color={c.status === "ACTIVE" ? "success" : c.status === "PAUSED" ? "warning" : "error"}>
                    {c.status === "ACTIVE" ? "同步中" : c.status === "PAUSED" ? "已暂停" : "异常"}
                  </Tag>
                </div>
                <Descriptions
                  size="small"
                  column={1}
                  items={[
                    { key: "provider", label: "提供方", children: c.providerKey },
                    { key: "mode", label: "同步模式", children: c.syncMode === "MANUAL" ? "手动" : c.syncMode === "SCHEDULED" ? "定时" : "Webhook" },
                    { key: "last", label: "最近成功", children: c.lastSuccessAt ? formatDateTime(c.lastSuccessAt) : "—" },
                    { key: "cursor", label: "游标年龄", children: `${c.cursorAgeMin} 分钟${c.cursorAgeMin > 240 ? "（超新鲜度 SLA）" : ""}` },
                    { key: "sync", label: "本轮同步", children: `发现 ${c.counts.discovered} · 新增 ${c.counts.created} · 更新 ${c.counts.updated} · 删除 ${c.counts.deleted} · 失败 ${c.counts.failed}` },
                  ]}
                />
                {c.lastErrorCode ? <p style={{ color: "var(--danger)", fontSize: 12, marginTop: 8 }}>最近错误：{c.lastErrorCode}</p> : null}
                <Space style={{ marginTop: 12 }}>
                  <Button size="small" icon={<ReloadOutlined />} onClick={() => toast("info", "mock：已触发一次手动同步")}>立即同步</Button>
                  <Button size="small" onClick={() => toast("info", "mock：同步任务详情与单对象重放（契约待冻结）")}>任务详情</Button>
                </Space>
              </Card>
            ))}
            {(connectors.data?.length ?? 0) === 0 ? (
              <Card style={{ gridColumn: "1 / -1" }}>
                <Empty icon="🔌" title="未接入连接器" desc="支持对象存储、SharePoint/OneDrive、Confluence 等来源（首批范围待评审）" />
              </Card>
            ) : null}
          </div>
        )
      ) : null}

      {tab === "settings" ? (
        <div style={{ display: "flex", flexDirection: "column", gap: 16, maxWidth: 720 }}>
          <KbSettingsForm key={data.id} kb={data} canEdit={data.role !== "VIEWER"} onSaved={() => kb.reload()} />
          <div className="card" style={{ borderColor: "var(--danger)" }}>
            <h3 className="card-title" style={{ color: "var(--danger)" }}>危险操作</h3>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12 }}>
              <div>
                <div style={{ fontWeight: 500 }}>归档知识库</div>
                <div style={{ fontSize: 12, color: "var(--text-3)" }}>归档后不可上传与问答，可随时恢复</div>
              </div>
              <Button danger disabled={!isOwner} title={isOwner ? undefined : "仅所有者可操作"} onClick={() => setArchiveOpen(true)}>
                归档
              </Button>
            </div>
          </div>
        </div>
      ) : null}

      <Modal
        title="归档知识库"
        open={archiveOpen}
        okText="确认归档"
        cancelText="取消"
        confirmLoading={archiving}
        okButtonProps={{ danger: true }}
        onCancel={() => setArchiveOpen(false)}
        onOk={() => {
          setArchiving(true);
          setTimeout(() => {
            setArchiving(false);
            setArchiveOpen(false);
            toast("success", "知识库已归档（mock）");
            router.push("/kbs");
          }, 600);
        }}
      >
        <div>
          将归档「<strong>{data.name}</strong>」：{data.documentCount} 篇文档将不可检索与问答，{data.members.length} 名成员仅可查看。
          该操作可恢复，但传播期间按安全策略立即收紧访问。
        </div>
      </Modal>
    </div>
  );
}
