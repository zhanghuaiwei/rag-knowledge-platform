"use client";

import { Button, Table, Tag } from "antd";
import type { TableColumnsType } from "antd";

import type { DocumentVersion, IngestStatus } from "@/api-client";
import { formatDateTime, formatFileSize, statusText } from "@/lib/format";

function versionColumns(currentVersionNo: number, onRollback: (versionNo: number) => void): TableColumnsType<DocumentVersion> {
  return [
    {
      title: "版本",
      dataIndex: "versionNo",
      render: (v: number) => (
        <>
          v{v}
          {v === currentVersionNo ? <Tag color="blue" style={{ marginLeft: 6 }}>当前</Tag> : null}
        </>
      ),
    },
    { title: "大小", dataIndex: "fileSize", render: (v: number) => formatFileSize(v) },
    {
      title: "摄取状态",
      dataIndex: "ingestStatus",
      render: (v: IngestStatus) => {
        const [label, color] = statusText("ingest", v);
        return <Tag color={color}>{label}</Tag>;
      },
    },
    {
      title: "安全扫描",
      dataIndex: "safetyStatus",
      render: (v: DocumentVersion["safetyStatus"]) => {
        const text = v === "PASSED" ? "通过" : v === "PENDING" ? "待扫描" : v === "BLOCKED" ? "阻断" : "失败";
        const color = v === "PASSED" ? "success" : v === "BLOCKED" || v === "FAILED" ? "error" : "warning";
        return <Tag color={color}>{text}</Tag>;
      },
    },
    { title: "分块", dataIndex: "chunkCount", width: 70 },
    { title: "创建", key: "created", render: (_, v) => `${v.createdBy} · ${formatDateTime(v.createdAt)}` },
    {
      title: "操作",
      key: "action",
      width: 90,
      render: (_, v) =>
        v.versionNo !== currentVersionNo ? (
          <Button size="small" type="link" onClick={() => onRollback(v.versionNo)}>
            回滚
          </Button>
        ) : null,
    },
  ];
}

/** 文档版本时间线：不可变版本 + 当前版本指针，支持回滚（F2.2-4.2.6）。 */
export function VersionHistory({
  versions,
  currentVersionNo,
  rollingBack,
  onRollback,
}: {
  versions: DocumentVersion[];
  currentVersionNo: number;
  rollingBack: boolean;
  onRollback: (versionNo: number) => void;
}) {
  return (
    <Table<DocumentVersion>
      rowKey="versionNo"
      columns={versionColumns(currentVersionNo, onRollback)}
      dataSource={versions}
      loading={rollingBack}
      pagination={false}
      size="small"
    />
  );
}
