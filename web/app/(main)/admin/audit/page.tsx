"use client";

import { useState } from "react";
import { Button, Card, Pagination, Select, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { DownloadOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { AuditLog } from "@/api-client";
import { Empty, ErrorState, SkeletonRows } from "@/components/async-state";
import { useToast } from "@/components/feedback";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const RESULT_TAG: Record<AuditLog["result"], [string, string]> = {
  SUCCEEDED: ["成功", "success"],
  DENIED: ["被拒绝", "error"],
  FAILED: ["失败", "warning"],
};

export default function AuditPage() {
  const toast = useToast();
  const [page, setPage] = useState(1);
  const [resultFilter, setResultFilter] = useState("");
  // 筛选下沉为查询参数（服务端过滤 + 正确分页），不再前端过滤当前页
  const logs = useAsync(
    () => api.listAuditLogs({ page, size: 12, result: (resultFilter || undefined) as AuditLog["result"] | undefined }),
    [page, resultFilter],
  );

  const copyRequestId = async (requestId: string) => {
    try {
      await navigator.clipboard.writeText(requestId);
      toast("success", "requestId 已复制，可用于串联日志与 trace");
    } catch {
      toast("info", requestId);
    }
  };

  const columns: TableColumnsType<AuditLog> = [
    { title: "时间", dataIndex: "occurredAt", width: 150, render: (v: string) => <span style={{ whiteSpace: "nowrap" }}>{formatDateTime(v)}</span> },
    {
      title: "操作者",
      key: "actor",
      render: (_, log) => (
        <>
          {log.actor}{" "}
          <Tag color={log.actorType === "USER" ? "blue" : log.actorType === "API_KEY" ? "processing" : "default"}>{log.actorType}</Tag>
        </>
      ),
    },
    { title: "动作", dataIndex: "action" },
    { title: "资源", dataIndex: "resourceType", render: (v: string, log) => `${v} #${log.resourceId}` },
    {
      title: "结果",
      dataIndex: "result",
      width: 90,
      render: (v: AuditLog["result"]) => {
        const [label, color] = RESULT_TAG[v];
        return <Tag color={color}>{label}</Tag>;
      },
    },
    { title: "原因码", dataIndex: "reasonCode", width: 110, render: (v: string | null) => v ?? "—" },
    {
      title: "requestId",
      dataIndex: "requestId",
      width: 130,
      render: (v: string) => (
        <Typography.Link onClick={() => void copyRequestId(v)} title="点击复制 requestId">
          {v.slice(0, 8)}…
        </Typography.Link>
      ),
    },
  ];

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · 审计日志</h1>
          <p className="page-desc">追加写语义，记录策略版本与操作结果；导出遵循最小权限与脱敏</p>
        </div>
        <div className="page-actions">
          <Select
            value={resultFilter || undefined}
            onChange={(v) => { setResultFilter(v ?? ""); setPage(1); }}
            allowClear
            placeholder="全部结果"
            style={{ width: 130 }}
            options={[
              { value: "SUCCEEDED", label: "成功" },
              { value: "DENIED", label: "被拒绝" },
              { value: "FAILED", label: "失败" },
            ]}
          />
          {/* 审计导出按钮：后端 /analytics/export 白名单（usage/costs/top-documents/dau）暂无 audit 类别，
              审计导出需权限校验与脱敏契约冻结后才可接通，点击给出明确缺口提示而非模拟下载 */}
          <Button
            icon={<DownloadOutlined />}
            onClick={() =>
              toast("info", "审计导出暂未接通：需权限校验与脱敏契约冻结；后端现有 /analytics/export 仅支持 usage/costs/top-documents/dau")
            }
          >
            导出
          </Button>
        </div>
      </div>

      {logs.loading ? (
        <Card>
          <SkeletonRows rows={8} />
        </Card>
      ) : logs.error ? (
        <Card>
          <ErrorState message={logs.error} onRetry={logs.reload} />
        </Card>
      ) : (
        <Card>
          <Table<AuditLog>
            rowKey="id"
            columns={columns}
            dataSource={logs.data?.items ?? []}
            scroll={{ x: 960 }}
            pagination={false}
            locale={{ emptyText: <Empty icon="🧾" title="无匹配日志" /> }}
          />
          {logs.data ? (
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 16 }}>
              <Pagination current={page} pageSize={12} total={logs.data.total} onChange={setPage} showTotal={(t) => `共 ${t} 条`} />
            </div>
          ) : null}
        </Card>
      )}
    </div>
  );
}
