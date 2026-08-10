"use client";

import { useState } from "react";

import { api } from "@/api-client";
import { Icon } from "@/components/icons";
import { Empty, ErrorState, SkeletonRows, Switch, Tag, useToast } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

export default function WebhooksPage() {
  const toast = useToast();
  const webhooks = useAsync(() => api.listWebhooks());
  const [paused, setPaused] = useState<Set<number>>(new Set());
  const [pendingId, setPendingId] = useState<number | null>(null);

  const toggle = (id: number, currentlyPaused: boolean) => {
    setPendingId(id);
    setTimeout(() => {
      setPaused((prev) => {
        const next = new Set(prev);
        if (currentlyPaused) next.delete(id);
        else next.add(id);
        return next;
      });
      setPendingId(null);
      toast("success", currentlyPaused ? "Webhook 已恢复投递（mock）" : "Webhook 已暂停（mock）");
    }, 400);
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1 className="page-title">管理中心 · Webhook</h1>
          <p className="page-desc">签名投递、重试退避与死信队列；禁止内网与元数据地址</p>
        </div>
        <div className="page-actions">
          <button className="btn btn-primary" onClick={() => toast("info", "mock：创建 Webhook（egress allowlist 校验，契约待冻结）")}>
            <Icon name="plus" size={15} /> 创建 Webhook
          </button>
        </div>
      </div>

      {webhooks.loading ? (
        <div className="card"><SkeletonRows rows={3} /></div>
      ) : webhooks.error ? (
        <div className="card"><ErrorState message={webhooks.error} onRetry={webhooks.reload} /></div>
      ) : (webhooks.data?.length ?? 0) === 0 ? (
        <div className="card"><Empty icon="🔗" title="暂无 Webhook" desc="订阅文档发布、审核、同步等事件" /></div>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>名称</th><th>目标地址</th><th>事件类型</th><th>创建时间</th><th>投递状态</th></tr></thead>
            <tbody>
              {webhooks.data?.map((hook) => {
                const isPaused = paused.has(hook.id) || hook.status === "PAUSED";
                return (
                  <tr key={hook.id}>
                    <td style={{ fontWeight: 500 }}>{hook.name}</td>
                    <td><code style={{ fontSize: 12 }}>{hook.targetUrl}</code></td>
                    <td>
                      <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
                        {hook.eventTypes.map((t) => <Tag key={t} color="info">{t}</Tag>)}
                      </div>
                    </td>
                    <td>{formatDateTime(hook.createdAt).slice(0, 10)}</td>
                    <td>
                      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                        <Switch checked={!isPaused} disabled={pendingId === hook.id} onChange={() => toggle(hook.id, isPaused)} />
                        <Tag color={isPaused ? "warning" : "success"}>{isPaused ? "已暂停" : "投递中"}</Tag>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
