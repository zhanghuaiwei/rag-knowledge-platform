"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button, Card, List, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";
import { MessageOutlined, PlusOutlined, RightOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { TopDocument } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { LineChart, Donut } from "@/components/charts";
import { Icon } from "@/components/icons";
import { StatCard } from "@/components/stat-card";
import { formatNumber, formatPercent, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const topColumns: TableColumnsType<TopDocument> = [
  {
    title: "文档",
    dataIndex: "fileName",
    render: (v: string) => <span style={{ fontWeight: 500 }}>{v}</span>,
  },
  { title: "知识库", dataIndex: "kbName" },
  { title: "问答引用", dataIndex: "qaCount", render: (v: number) => formatNumber(v) },
  { title: "搜索命中", dataIndex: "searchCount", render: (v: number) => formatNumber(v) },
];

export default function DashboardPage() {
  const router = useRouter();
  const health = useAsync(() => api.getKnowledgeHealth());
  const usage = useAsync(() => api.getDailyUsage());
  const kbs = useAsync(() => api.listKbs({ page: 1, size: 4 }));
  const sessions = useAsync(() => api.listChatSessions({ page: 1, size: 5 }));
  const topDocs = useAsync(() => api.getTopDocuments());
  const reviews = useAsync(() => api.listReviews({ page: 1, size: 3 }));
  const failedDocs = useAsync(() => api.listDocuments({ ingestStatus: "FAILED", page: 1, size: 3 }));

  const latestUsage = usage.data?.at(-1);
  const answeredRate = health.data ? 1 - health.data.noAnswerRate : 0;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">工作台</h1>
          <p className="page-desc">你的知识资产与问答质量一览</p>
        </div>
        <div className="page-actions">
          <Button type="primary" icon={<MessageOutlined />} onClick={() => router.push("/chat")}>
            发起问答
          </Button>
          <Button icon={<PlusOutlined />} onClick={() => router.push("/kbs/new")}>
            新建知识库
          </Button>
        </div>
      </div>

      {health.error ? (
        <ErrorState message={health.error} onRetry={health.reload} />
      ) : (
        <div className="grid grid-4">
          <StatCard icon="chat" label="昨日问答" value={latestUsage ? formatNumber(latestUsage.qaCount) : undefined} loading={usage.loading} extra={latestUsage ? `搜索 ${formatNumber(latestUsage.searchCount)} 次` : undefined} />
          <StatCard icon="check" label="回答率" value={health.data ? formatPercent(answeredRate) : undefined} loading={health.loading} extra={health.data ? `无答案 ${formatPercent(health.data.noAnswerRate)}` : undefined} />
          <StatCard icon="alert" label="低置信率" value={health.data ? formatPercent(health.data.lowConfRate) : undefined} loading={health.loading} extra={health.data ? `平均置信 ${health.data.averageConfidence.toFixed(2)}` : undefined} danger={(health.data?.lowConfRate ?? 0) > 0.15} />
          <StatCard icon="clock" label="知识新鲜度" value={health.data ? formatPercent(health.data.freshnessScore) : undefined} loading={health.loading} extra="按来源同步与复审期计算" />
        </div>
      )}

      <div className="grid grid-23">
        <Card title="近 14 天问答趋势">
          {usage.loading ? (
            <SkeletonRows rows={3} />
          ) : usage.error ? (
            <ErrorState message={usage.error} onRetry={usage.reload} />
          ) : (
            <LineChart points={(usage.data ?? []).map((d) => ({ label: d.date.slice(5), value: d.qaCount }))} height={220} />
          )}
        </Card>
        <Card
          title="回答质量"
          extra={
            <Tag color={health.data ? "success" : "default"}>{health.data ? `回答率 ${formatPercent(answeredRate)}` : "加载中…"}</Tag>
          }
        >
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12, padding: "8px 0" }}>
            {health.loading ? (
              <SkeletonRows rows={2} />
            ) : health.error ? (
              <ErrorState message={health.error} onRetry={health.reload} />
            ) : (
              <Donut ratio={answeredRate} size={140} label="回答率" />
            )}
            <p style={{ color: "var(--text-3)", fontSize: 12, textAlign: "center", margin: 0 }}>
              拒答是安全行为：无权限或低置信内容不会强行生成答案
            </p>
          </div>
        </Card>
      </div>

      <div className="grid grid-3">
        <Card
          title="我的知识库"
          extra={<Link href="/kbs" style={{ fontSize: 12 }}>全部 →</Link>}
        >
          {kbs.loading ? (
            <SkeletonRows rows={3} />
          ) : (kbs.data?.items.length ?? 0) === 0 ? (
            <Empty icon="📚" title="暂无知识库" desc="创建一个知识库开始沉淀知识" />
          ) : (
            <List
              size="small"
              dataSource={kbs.data?.items ?? []}
              renderItem={(kb) => (
                <List.Item
                  onClick={() => router.push(`/kbs/${kb.id}`)}
                  style={{ cursor: "pointer" }}
                >
                  <List.Item.Meta
                    avatar={<Icon name="kb" size={18} />}
                    title={kb.name}
                    description={`${kb.documentCount} 文档 · ${formatNumber(kb.chunkCount)} 分块`}
                  />
                  <Tag color={statusText("kbRole", kb.role)[1]}>{statusText("kbRole", kb.role)[0]}</Tag>
                </List.Item>
              )}
            />
          )}
        </Card>

        <Card title="最近问答" extra={<Link href="/chat" style={{ fontSize: 12 }}>全部 →</Link>}>
          {sessions.loading ? (
            <SkeletonRows rows={3} />
          ) : (
            <List
              size="small"
              dataSource={sessions.data?.items ?? []}
              renderItem={(s) => (
                <List.Item
                  onClick={() => router.push(`/chat?session=${s.id}`)}
                  style={{ cursor: "pointer" }}
                >
                  <List.Item.Meta
                    title={s.title}
                    description={`${s.messageCount} 条消息 · ${formatRelative(s.updatedAt)}`}
                  />
                  <RightOutlined style={{ fontSize: 12, color: "var(--text-3)" }} />
                </List.Item>
              )}
            />
          )}
        </Card>

        <Card title="待办事项">
          {reviews.loading || failedDocs.loading ? (
            <SkeletonRows rows={3} />
          ) : (
            <List
              size="small"
              dataSource={[
                ...(reviews.data?.items ?? []).map((r) => ({ type: "review" as const, id: r.documentId, title: r.title, sub: `${r.submitter} 提交 · ${formatRelative(r.submittedAt)}`, href: "/governance/review" })),
                ...(failedDocs.data?.items ?? []).map((d) => ({ type: "failed" as const, id: d.id, title: d.title, sub: d.kbName, href: `/documents/${d.id}` })),
              ]}
              locale={{ emptyText: <Empty icon="✅" title="全部处理完毕" /> }}
              renderItem={(item) => (
                <List.Item onClick={() => router.push(item.href)} style={{ cursor: "pointer" }}>
                  <Tag color={item.type === "review" ? "warning" : "error"}>{item.type === "review" ? "待审核" : "摄取失败"}</Tag>
                  <div style={{ marginLeft: 8, flex: 1, minWidth: 0 }}>
                    <div style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{item.title}</div>
                    <div style={{ fontSize: 12, color: "var(--text-3)" }}>{item.sub}</div>
                  </div>
                </List.Item>
              )}
            />
          )}
        </Card>
      </div>

      <Card title="热门文档 Top 5">
        {topDocs.loading ? (
          <SkeletonRows rows={3} />
        ) : topDocs.error ? (
          <ErrorState message={topDocs.error} onRetry={topDocs.reload} />
        ) : (
          <Table<TopDocument>
            rowKey="documentId"
            columns={topColumns}
            dataSource={(topDocs.data ?? []).slice(0, 5)}
            pagination={false}
            onRow={(d) => ({ onClick: () => router.push(`/documents/${d.documentId}`) })}
          />
        )}
      </Card>
    </div>
  );
}
