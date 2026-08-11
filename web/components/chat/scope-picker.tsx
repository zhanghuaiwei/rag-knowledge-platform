"use client";

import { useEffect, useState } from "react";
import { Modal, Select, Tag } from "antd";

import { api } from "@/api-client";
import { useAsync } from "@/lib/use-async";

/**
 * 问答知识库范围选择器：新会话按选中知识库检索（最多 5 个，F2.1 用例步骤 3）。
 * 选择结果通过 createChatSession 固化到会话。
 */
export function ScopePicker({
  open,
  onClose,
  onConfirm,
  value,
  maxCount = 5,
}: {
  open: boolean;
  onClose: () => void;
  onConfirm: (ids: number[]) => void;
  value: number[];
  maxCount?: number;
}) {
  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));
  const [selected, setSelected] = useState<number[]>(value);

  useEffect(() => {
    if (open) setSelected(value);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const options = (kbs.data?.items ?? [])
    .filter((kb) => kb.status === "ACTIVE")
    .map((kb) => ({ value: kb.id, label: kb.name }));

  return (
    <Modal
      title="选择问答知识库范围"
      open={open}
      okText="确认范围"
      cancelText="取消"
      onOk={() => {
        onConfirm(selected);
        onClose();
      }}
      onCancel={onClose}
    >
      <Select
        mode="multiple"
        maxCount={maxCount}
        value={selected}
        onChange={setSelected}
        options={options}
        placeholder={`选择 1-${maxCount} 个知识库`}
        style={{ width: "100%" }}
      />
      <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 12 }}>
        新会话将按此范围检索；最多同时选择 {maxCount} 个知识库。已选：
        <Tag style={{ marginLeft: 6 }}>{selected.length} 个</Tag>
      </p>
    </Modal>
  );
}
