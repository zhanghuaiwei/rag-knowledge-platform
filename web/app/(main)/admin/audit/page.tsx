"use client";

import { useState } from "react";

import { api } from "@/api-client";
import { Icon } from "@/components/icons";
import { Empty, ErrorState, Pagination, SkeletonRows, Tag, useToast } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const RESULT_TEXT: Record<string, [string, string]> = {
  SUCCEEDED: ["成功", "success"],
  DENIED: ["被拒绝", "danger"],
  FAILED: ["失败", "warning"],
};

export default function AuditPage() {
  const toast = useToast();
  const [page, setPage] = useState(1);
  const [resultFilter, setResultFilter] = useState("");
  // 筛选下沉为查询参数（服务端过滤 + 正确分页），不再前端过滤当前页
  const logs = useAsync(
    () => api.listAuditLogs({ page, size: 12, result: (resultFilter || undefined) as "SUCCEEDED" | "DENIED" | "FAILED" | undefined }),
    [page, resultFilter],
  );

  const items = logs.data?.items ?? [];

  const copyRequestId = async (requestId: string) => {
    try {
      await navigator.clipboard.writeText(requestId);
      toast("success", "requestId 已复制，可用于串联日志与 trace");
    } catch {
      toast("info", requestId);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · 审计日志</h1>
          <p className="page-desc">追加写语义，记录策略版本与操作结果；导出遵循最小权限与脱敏</p>
        </div>
        <div className="page-actions">
          <select className="select" style={{ width: "auto" }} value={resultFilter} onChange={(e) => { setResultFilter(e.target.value); setPage(1); }} aria-label="结果筛选">
            <option value="">全部结果</option>
            <option value="SUCCEEDED">成功</option>
            <option value="DENIED">被拒绝</option>
            <option value="FAILED">失败</option>
          </select>
          <button className="btn" onClick={() => toast("info", "mock：审计导出需权限校验与脱敏（契约待冻结）")}>
            <Icon name="download" size={15} /> 导出
          </button>
        </div>
      </div>

      {logs.loading ? (
        <div className="card"><SkeletonRows rows={8} /></div>
      ) : logs.error ? (
        <div className="card"><ErrorState message={logs.error} onRetry={logs.reload} /></div>
      ) : items.length === 0 ? (
        <div className="card"><Empty icon="🧾" title="无匹配日志" /></div>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>时间</th><th>操作者</th><th>动作</th><th>资源</th><th>结果</th><th>原因码</th><th>requestId</th></tr></thead>
            <tbody>
              {items.map((log) => (
                <tr key={log.id}>
                  <td style={{ whiteSpace: "nowrap" }}>{formatDateTime(log.occurredAt)}</td>
                  <td>
                    {log.actor}
                    <Tag color={log.actorType === "USER" ? "primary" : log.actorType === "API_KEY" ? "info" : ""}>{log.actorType}</Tag>
                  </td>
                  <td>{log.action}</td>
                  <td>{log.resourceType} #{log.resourceId}</td>
                  <td><Tag color={RESULT_TEXT[log.result]?.[1] ?? ""}>{RESULT_TEXT[log.result]?.[0] ?? log.result}</Tag></td>
                  <td>{log.reasonCode ?? "—"}</td>
                  <td>
                    <button className="btn btn-sm btn-ghost" onClick={() => void copyRequestId(log.requestId)} title="点击复制 requestId">
                      <Icon name="copy" size={12} /> {log.requestId.slice(0, 8)}…
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {logs.data ? <Pagination page={page} size={12} total={logs.data.total} onChange={setPage} /> : null}
    </div>
  );
}
