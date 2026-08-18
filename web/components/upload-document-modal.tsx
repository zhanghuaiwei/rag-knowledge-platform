"use client";

import { useEffect, useRef, useState } from "react";
import { Button, Form, Input, Modal, Select } from "antd";
import { UploadOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import type { DocumentSummary, Sensitivity } from "@/api-client";
import { useToast } from "@/components/feedback";
import { formatFileSize } from "@/lib/format";
import { useAsync } from "@/lib/use-async";

const ACCEPT = ".pdf,.doc,.docx,.md,.txt,.pptx,.xlsx,.csv,.html";
const MAX_SIZE = 50 * 1024 * 1024;

interface UploadFormValues {
  kbId: number;
  title?: string;
  sensitivity: Sensitivity;
}

/**
 * 文档上传弹窗：经 api.uploadDocument 走真实分片上传
 * （/upload/init → 逐分片 PUT → complete 轮询任务终态），
 * 完成后文档进入安全扫描与解析队列（GKB-03），摄取状态从 PARSING 开始。
 */
export function UploadDocumentModal({
  open,
  defaultKbId,
  onClose,
  onUploaded,
}: {
  open: boolean;
  defaultKbId?: number;
  onClose: () => void;
  onUploaded: (doc: DocumentSummary) => void;
}) {
  const toast = useToast();
  const kbs = useAsync(() => api.listKbs({ page: 1, size: 50 }));
  const [form] = Form.useForm<UploadFormValues>();
  const fileRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  // 打开时按上下文重置表单
  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({ kbId: defaultKbId, title: "", sensitivity: "INTERNAL" });
    setFile(null);
    setError("");
    setSubmitting(false);
  }, [open, defaultKbId, form]);

  const pickFile = (selected: File | null) => {
    setFile(selected);
    setError("");
    if (selected && !form.getFieldValue("title")) {
      form.setFieldValue("title", selected.name.replace(/\.[^.]+$/, ""));
    }
  };

  const submit = async () => {
    if (!file) {
      setError("请选择要上传的文件");
      return;
    }
    if (file.size > MAX_SIZE) {
      setError(`文件超过 ${formatFileSize(MAX_SIZE)} 上限`);
      return;
    }
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      const doc = await api.uploadDocument({
        kbId: values.kbId,
        title: values.title?.trim() || file.name,
        fileName: file.name,
        fileSize: file.size,
        sensitivity: values.sensitivity,
        file, // 文件字节：http 传输层按服务端返回的 partSize 切片直传
      });
      toast("success", `「${doc.title}」已上传，进入解析队列`);
      onUploaded(doc);
      onClose();
    } catch (err: unknown) {
      if (err && typeof err === "object" && "errorFields" in err) return; // 表单校验失败，不额外提示
      setError(err instanceof Error ? err.message : "上传失败，请重试");
      setSubmitting(false);
    }
  };

  const kbOptions = (kbs.data?.items ?? [])
    .filter((kb) => kb.role !== "VIEWER" && kb.status === "ACTIVE")
    .map((kb) => ({ value: kb.id, label: kb.name }));

  return (
    <Modal
      title="上传文档"
      open={open}
      onCancel={submitting ? undefined : onClose}
      confirmLoading={submitting}
      okText={submitting ? "上传中…" : "开始上传"}
      onOk={() => void submit()}
    >
      <Form<UploadFormValues> form={form} layout="vertical" requiredMark={false} initialValues={{ sensitivity: "INTERNAL" }}>
        <Form.Item name="kbId" label="目标知识库" rules={[{ required: true, message: "请选择目标知识库" }]}>
          <Select placeholder="请选择" options={kbOptions} aria-label="目标知识库" />
        </Form.Item>
        <Form.Item label="文件" required>
          <input
            ref={fileRef}
            type="file"
            accept={ACCEPT}
            style={{ display: "none" }}
            onChange={(e) => pickFile(e.target.files?.[0] ?? null)}
          />
          <Button block icon={<UploadOutlined />} onClick={() => fileRef.current?.click()}>
            {file ? `${file.name}（${formatFileSize(file.size)}）` : "选择文件（PDF / Word / Markdown 等）"}
          </Button>
          <div className="field-hint">单文件不超过 {formatFileSize(MAX_SIZE)}；上传后先经安全扫描再进入解析队列</div>
        </Form.Item>
        <Form.Item name="title" label="标题">
          <Input placeholder="默认取文件名" maxLength={80} />
        </Form.Item>
        <Form.Item name="sensitivity" label="敏感级">
          <Select
            aria-label="敏感级"
            options={[
              { value: "PUBLIC", label: "公开" },
              { value: "INTERNAL", label: "内部" },
              { value: "CONFIDENTIAL", label: "机密" },
              { value: "RESTRICTED", label: "受限" },
            ]}
          />
        </Form.Item>
      </Form>
      {error ? <p className="field-error" style={{ marginTop: 8 }}>{error}</p> : null}
    </Modal>
  );
}
