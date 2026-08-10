"use client";

import { Input, Modal, Select } from "antd";

const TYPE_OPTIONS = [
  { value: "WRONG", label: "内容错误" },
  { value: "STALE", label: "信息过期" },
  { value: "NO_PERMISSION", label: "引用无权限" },
  { value: "CITATION", label: "引用不符" },
];

/** 问答反馈弹窗（antd Modal + Form 控件）。 */
export function ChatFeedbackModal({
  open,
  type,
  note,
  onTypeChange,
  onNoteChange,
  onClose,
  onSubmit,
}: {
  open: boolean;
  type: string;
  note: string;
  onTypeChange: (type: string) => void;
  onNoteChange: (note: string) => void;
  onClose: () => void;
  onSubmit: () => void;
}) {
  return (
    <Modal title="反馈问题" open={open} onCancel={onClose} okText="提交反馈" cancelText="取消" onOk={onSubmit}>
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontWeight: 500, marginBottom: 8 }}>问题类型</div>
        <Select style={{ width: "100%" }} value={type} onChange={onTypeChange} options={TYPE_OPTIONS} aria-label="问题类型" />
      </div>
      <div>
        <div style={{ fontWeight: 500, marginBottom: 8 }}>补充说明（可选）</div>
        <Input.TextArea placeholder="描述你遇到的问题…" value={note} maxLength={200} onChange={(e) => onNoteChange(e.target.value)} showCount rows={4} />
      </div>
    </Modal>
  );
}
