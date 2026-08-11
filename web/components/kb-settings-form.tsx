"use client";

import { useEffect, useState } from "react";
import { Button, Form, Input, Switch } from "antd";

import { api } from "@/api-client";
import type { Kb } from "@/api-client";
import { useToast } from "@/components/feedback";

/** 知识库基本设置表单（antd Form）：受控，保存走 updateKb 契约（mock 下直接写内存库）。 */
export function KbSettingsForm({ kb, canEdit, onSaved }: { kb: Kb; canEdit: boolean; onSaved: () => void }) {
  const toast = useToast();
  const [form] = Form.useForm<{ name: string; description: string; requiresReview: boolean }>();
  const [saving, setSaving] = useState(false);

  const name = Form.useWatch("name", form);
  const description = Form.useWatch("description", form);
  const requiresReview = Form.useWatch("requiresReview", form);
  const dirty = name !== kb.name || description !== kb.description || requiresReview !== kb.requiresReview;

  useEffect(() => {
    form.setFieldsValue({ name: kb.name, description: kb.description, requiresReview: kb.requiresReview });
  }, [kb, form]);

  const onFinish = async (values: { name: string; description: string; requiresReview: boolean }) => {
    setSaving(true);
    try {
      await api.updateKb(kb.id, { name: values.name.trim(), description: values.description.trim(), requiresReview: values.requiresReview });
      toast("success", "设置已保存");
      onSaved();
    } catch (err) {
      toast("error", err instanceof Error ? err.message : "保存失败，请重试");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="card">
      <h3 className="card-title">基本设置</h3>
      <Form form={form} layout="vertical" requiredMark={false} onFinish={onFinish} style={{ maxWidth: 480 }}>
        <Form.Item name="name" label="名称" rules={[{ required: true, message: "名称不能为空" }, { max: 40, message: "名称不超过 40 字" }]}>
          <Input disabled={!canEdit} maxLength={40} />
        </Form.Item>
        <Form.Item name="description" label="描述">
          <Input.TextArea disabled={!canEdit} maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="requiresReview" label="发布前审核" valuePropName="checked" extra="调整仅影响新提交内容">
          <Switch disabled={!canEdit} />
        </Form.Item>
        <Button type="primary" htmlType="submit" loading={saving} disabled={!canEdit || !dirty}>
          保存设置
        </Button>
      </Form>
    </div>
  );
}
