"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

import { api } from "@/api-client";
import { LineChart, Donut } from "@/components/charts";
import { Icon, type IconName } from "@/components/icons";
import { Empty, ErrorState, SkeletonRows, Tag } from "@/components/ui";
import { formatNumber, formatPercent, formatRelative, statusText } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

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
          <Link href="/chat" className="btn btn-primary">
            <Icon name="chat" size={15} /> 发起问答
          </Link>
          <Link href="/kbs/new" className="btn">
            <Icon name="plus" size={15} /> 新建知识库
          </Link>
        </div>
      </div>

      {health.error ? (
        <ErrorState message={health.error} onRetry={health.reload} />
      ) : (
        <div className="grid grid-4">
          {(
            [
              { icon: "chat", label: "昨日问答", text: latestUsage ? formatNumber(latestUsage.qaCount) : undefined, loading: usage.loading, extra: latestUsage ? `搜索 ${formatNumber(latestUsage.searchCount)} 次` : undefined },
              { icon: "check", label: "回答率", text: health.data ? formatPercent(answeredRate) : undefined, loading: health.loading, extra: health.data ? `无答案 ${formatPercent(health.data.noAnswerRate)}` : undefined },
              { icon: "alert", label: "低置信率", text: health.data ? formatPercent(health.data.lowConfRate) : undefined, loading: health.loading, extra: health.data ? `平均置信 ${health.data.averageConfidence.toFixed(2)}` : undefined, warn: (health.data?.lowConfRate ?? 0) > 0.15 },
              { icon: "clock", label: "知识新鲜度", text: health.data ? formatPercent(health.data.freshnessScore) : undefined, loading: health.loading, extra: "按来源同步与复审期计算" },
            ] satisfies { icon: IconName; label: string; text?: string; loading: boolean; extra?: string; warn?: boolean }[]
          ).map((stat) => (
            <div key={stat.label} className="card stat-card">
              <span className="stat-label">
                <Icon name={stat.icon} size={15} /> {stat.label}
              </span>
              <span className="stat-value" style={stat.warn ? { color: "var(--danger)" } : undefined}>
                {stat.loading ? "…" : stat.text ?? "—"}
              </span>
              {stat.extra ? <span className="stat-extra">{stat.extra}</span> : null}
            </div>
          ))}
        </div>
      )}

      <div className="grid grid-23">
        <div className="card">
          <h3 className="card-title">近 14 天问答趋势</h3>
          {usage.loading ? (
            <SkeletonRows rows={3} />
          ) : usage.error ? (
            <ErrorState message={usage.error} onRetry={usage.reload} />
          ) : (
            <LineChart points={(usage.data ?? []).map((d) => ({ label: d.date.slice(5), value: d.qaCount }))} height={220} />
          )}
        </div>
        <div className="card" style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 12 }}>
          <h3 className="card-title" style={{ alignSelf: "stretch" }}>回答质量</h3>
          {health.loading ? (
            <SkeletonRows rows={2} />
          ) : health.error ? (
            <ErrorState message={health.error} onRetry={health.reload} />
          ) : (
            <Donut ratio={answeredRate} size={140} label="回答率" />
          )}
          <p style={{ color: "var(--text-3)", fontSize: 12, textAlign: "center" }}>
            拒答是安全行为：无权限或低置信内容不会强行生成答案
          </p>
        </div>
      </div>

      <div className="grid grid-3">
        <div className="card">
          <h3 className="card-title">
            我的知识库 <Link href="/kbs" style={{ fontSize: 12, color: "var(--primary)" }}>全部 →</Link>
          </h3>
          {kbs.loading ? (
            <SkeletonRows rows={3} />
          ) : (kbs.data?.items.length ?? 0) === 0 ? (
            <Empty icon="📚" title="暂无知识库" desc="创建一个知识库开始沉淀知识" />
          ) : (
            kbs.data?.items.map((kb) => (
              <Link key={kb.id} href={`/kbs/${kb.id}`} className="list-row">
                <span className="file-icon"><Icon name="kb" size={18} /></span>
                <span className="list-row-main">
                  <div className="list-row-title">{kb.name}</div>
                  <div className="list-row-sub">{kb.documentCount} 文档 · {formatNumber(kb.chunkCount)} 分块</div>
                </span>
                <Tag color={statusText("kbRole", kb.role)[1]}>{statusText("kbRole", kb.role)[0]}</Tag>
              </Link>
            ))
          )}
        </div>
        <div className="card">
          <h3 className="card-title">
            最近问答 <Link href="/chat" style={{ fontSize: 12, color: "var(--primary)" }}>全部 →</Link>
          </h3>
          {sessions.loading ? (
            <SkeletonRows rows={3} />
          ) : (
            sessions.data?.items.map((s) => (
              <Link key={s.id} href={`/chat?session=${s.id}`} className="list-row">
                <span className="list-row-main">
                  <div className="list-row-title">{s.title}</div>
                  <div className="list-row-sub">{s.messageCount} 条消息 · {formatRelative(s.updatedAt)}</div>
                </span>
                <Icon name="chevron-right" size={14} />
              </Link>
            ))
          )}
        </div>
        <div className="card">
          <h3 className="card-title">待办事项</h3>
          {reviews.loading || failedDocs.loading ? (
            <SkeletonRows rows={3} />
          ) : (
            <>
              {(reviews.data?.items ?? []).map((r) => (
                <Link key={r.documentId} href="/governance/review" className="list-row">
                  <Tag color="warning">待审核</Tag>
                  <span className="list-row-main">
                    <div className="list-row-title">{r.title}</div>
                    <div className="list-row-sub">{r.submitter} 提交 · {formatRelative(r.submittedAt)}</div>
                  </span>
                </Link>
              ))}
              {(failedDocs.data?.items ?? []).map((d) => (
                <Link key={d.id} href={`/documents/${d.id}`} className="list-row">
                  <Tag color="danger">摄取失败</Tag>
                  <span className="list-row-main">
                    <div className="list-row-title">{d.title}</div>
                    <div className="list-row-sub">{d.kbName}</div>
                  </span>
                </Link>
              ))}
              {(reviews.data?.items.length ?? 0) + (failedDocs.data?.items.length ?? 0) === 0 ? (
                <Empty icon="✅" title="全部处理完毕" />
              ) : null}
            </>
          )}
        </div>
      </div>

      <div className="card">
        <h3 className="card-title">热门文档 Top 5</h3>
        {topDocs.loading ? (
          <SkeletonRows rows={3} />
        ) : topDocs.error ? (
          <ErrorState message={topDocs.error} onRetry={topDocs.reload} />
        ) : (
          <div className="table-wrap" style={{ border: 0 }}>
            <table className="table">
              <thead>
                <tr><th>文档</th><th>知识库</th><th>问答引用</th><th>搜索命中</th></tr>
              </thead>
              <tbody>
                {(topDocs.data ?? []).slice(0, 5).map((d) => (
                  <tr key={d.documentId} className="clickable" onClick={() => router.push(`/documents/${d.documentId}`)}>
                    <td>{d.fileName}</td>
                    <td>{d.kbName}</td>
                    <td>{formatNumber(d.qaCount)}</td>
                    <td>{formatNumber(d.searchCount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
