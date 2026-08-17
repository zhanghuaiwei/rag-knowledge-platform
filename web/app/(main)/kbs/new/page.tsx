"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button, Descriptions, Form, Input, Radio, Select, Space, Steps, Switch } from "antd";
import { ArrowLeftOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import { useToast } from "@/components/feedback";

const STEPS = ["基本信息", "归属与治理", "策略与配额", "确认创建"] as const;

interface WizardForm {
  name: string;
  description: string;
  visibility: "PRIVATE" | "TENANT";
  owner: string;
  domain: string;
  sensitivity: string;
  retention: string;
  dataRegion: string;
  modelPolicy: string;
  requiresReview: boolean;
  ocrEnabled: boolean;
}

const INITIAL: WizardForm = {
  name: "",
  description: "",
  visibility: "PRIVATE",  // PRIVATE
  owner: "", // 张伟（当前用户）
  domain: "",
  sensitivity: "", // INTERNAL
  retention: "", // 3 年
  dataRegion: "", // 华东（上海）
  modelPolicy: "",  // 仅境内模型
  requiresReview: true,
  ocrEnabled: false,
};

/** 每步需要校验的字段名（仅校验当前步已挂载的表单项）。 */
const STEP_FIELDS: (keyof WizardForm)[][] = [
  ["name", "description", "visibility"],
  ["owner", "domain", "sensitivity", "retention", "requiresReview", "ocrEnabled"],
  ["dataRegion", "modelPolicy"],
  [],
];

export default function NewKbPage() {
  const router = useRouter();
  const toast = useToast();
  const [form] = Form.useForm<WizardForm>();
  const [step, setStep] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const allValues = Form.useWatch([], form);

  const nextStep = async () => {
    try {
      await form.validateFields(STEP_FIELDS[step]);
      setStep((s) => Math.min(s + 1, STEPS.length - 1));
    } catch {
      // 校验失败：antd 已在表单项展示错误
    }
  };

  const submit = async () => {
    try {
      await form.validateFields();
    } catch {
      return;
    }
    setSubmitting(true);
    try {
      const values = form.getFieldsValue();
      await api.createKb({
        name: values.name,
        description: values.description,
        visibility: values.visibility,
        domain: values.domain,
        sensitivity: values.sensitivity,
        retention: values.retention,
        dataRegion: values.dataRegion,
        modelPolicy: values.modelPolicy,
        requiresReview: values.requiresReview,
        ocrEnabled: values.ocrEnabled,
        tenant_id: "1",
      });
      toast("success", `知识库「${values.name}」已创建`);
      router.push("/kbs");
    } catch (err: unknown) {
      setSubmitting(false);
      toast("error", err instanceof Error ? err.message : "创建失败，请重试");
    }
  };

  const fieldProps = { style: { maxWidth: 460 } };

  return (
    <div className="page" style={{ maxWidth: 860, margin: "0 auto" }}>
      <div className="page-header">
        <div>
          <h1 className="page-title">新建知识库</h1>
          <p className="page-desc">治理与策略在创建时确定，后续可在设置中调整</p>
        </div>
        <Button icon={<ArrowLeftOutlined />} onClick={() => router.back()}>
          返回
        </Button>
      </div>

      <div className="card">
        <Steps current={step} items={STEPS.map((label) => ({ title: label }))} style={{ marginBottom: 32 }} />

        <Form<WizardForm> form={form} layout="vertical" requiredMark={false} initialValues={INITIAL}>
          <div style={{ display: step === 0 ? undefined : "none" }}>
            <Form.Item name="name" label="知识库名称" rules={[{ required: true, message: "请输入知识库名称" }, { min: 2, message: "名称至少 2 个字符" }]} {...fieldProps}>
              <Input placeholder="例如：产品研发知识库" maxLength={40} />
            </Form.Item>
            <Form.Item name="description" label="描述" rules={[{ max: 200, message: "描述不超过 200 字" }]} {...fieldProps}>
              <Input.TextArea placeholder="说明知识范围、适用人群与维护责任…" maxLength={200} showCount rows={3} />
            </Form.Item>
            <Form.Item name="visibility" label="可见性" {...fieldProps}>
              <Radio.Group
                options={[
                  { value: "PRIVATE", label: "私有（仅成员）" },
                  { value: "TENANT", label: "租户内可见" },
                ]}
              />
            </Form.Item>
          </div>

          <div style={{ display: step === 1 ? undefined : "none" }}>
            <Space direction="vertical" style={{ width: "100%" }} size={0}>
              <Form.Item name="owner" label="所有者" {...fieldProps}>
                <Input />
              </Form.Item>
              <Form.Item name="domain" label="业务域" rules={[{ required: true, message: "请选择业务域" }]} {...fieldProps}>
                <Select
                  placeholder="请选择"
                  options={["产品研发", "市场营销", "人力资源", "财务合规", "客户服务"].map((d) => ({ value: d, label: d }))}
                />
              </Form.Item>
              <Form.Item name="sensitivity" label="默认敏感级" {...fieldProps}>
                <Select
                  options={[
                    { value: "PUBLIC", label: "公开" },
                    { value: "INTERNAL", label: "内部" },
                    { value: "CONFIDENTIAL", label: "机密" },
                    { value: "RESTRICTED", label: "绝密" },
                  ]}
                />
              </Form.Item>
              <Form.Item name="retention" label="保留策略" {...fieldProps}>
                <Select options={["1 年", "3 年", "5 年", "永久保留"].map((r) => ({ value: r, label: r }))} />
              </Form.Item>
              <Form.Item name="requiresReview" label="发布前审核" valuePropName="checked" extra="文档解析就绪后需审核通过才可被检索">
                <Switch />
              </Form.Item>
              <Form.Item name="ocrEnabled" label="启用 OCR" valuePropName="checked" extra="扫描件与图片型 PDF 提取文字">
                <Switch />
              </Form.Item>
            </Space>
          </div>

          <div style={{ display: step === 2 ? undefined : "none" }}>
            <Space direction="vertical" style={{ width: "100%" }} size={0}>
              <Form.Item name="dataRegion" label="数据区域" extra="高敏内容不会路由到区域外的模型" {...fieldProps}>
                <Select options={["华东（上海）", "华北（北京）", "华南（深圳）"].map((r) => ({ value: r, label: r }))} />
              </Form.Item>
              <Form.Item name="modelPolicy" label="模型路由策略" {...fieldProps}>
                <Select options={["仅境内模型", "仅私有化模型", "按敏感级自动路由"].map((r) => ({ value: r, label: r }))} />
              </Form.Item>
            </Space>
            <div className="card" style={{ background: "var(--surface-2)", boxShadow: "none" }}>
              <h4 style={{ marginBottom: 10 }}>配额摘要</h4>
              <Descriptions
                size="small"
                column={1}
                items={[
                  { key: "storage", label: "存储上限", children: "50 GB（租户剩余 320 GB）" },
                  { key: "docs", label: "文档上限", children: "10,000 篇" },
                  { key: "profile", label: "索引 Profile", children: "standard-1024（创建后不可原地修改，换模走索引重建）" },
                ]}
              />
            </div>
          </div>

          <div style={{ display: step === 3 ? undefined : "none" }}>
            <h4 style={{ marginBottom: 12 }}>请确认以下配置</h4>
            <Descriptions
              className="card"
              size="small"
              column={1}
              items={[
                { key: "name", label: "名称", children: allValues?.name },
                { key: "visibility", label: "可见性", children: allValues?.visibility === "PRIVATE" ? "私有（仅成员）" : "租户内可见" },
                { key: "owner", label: "所有者 / 业务域", children: `${allValues?.owner} · ${allValues?.domain}` },
                {
                  key: "governance",
                  label: "治理",
                  children: `默认敏感级 ${allValues?.sensitivity} · 保留 ${allValues?.retention} · ${allValues?.requiresReview ? "发布前审核" : "免审核"}${allValues?.ocrEnabled ? " · OCR" : ""}`,
                },
                { key: "policy", label: "策略", children: `${allValues?.dataRegion} · ${allValues?.modelPolicy}` },
              ]}
            />
            <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 12 }}>
              创建后索引 Profile 绑定为不可变版本；换模仅通过「新索引全量重建 → 校验 → 别名原子切换」完成。
            </p>
          </div>
        </Form>

        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 24 }}>
          <Button onClick={() => setStep((s) => Math.max(s - 1, 0))} disabled={step === 0 || submitting}>
            上一步
          </Button>
          {step < STEPS.length - 1 ? (
            <Button type="primary" onClick={() => void nextStep()}>
              下一步
            </Button>
          ) : (
            <Button type="primary" loading={submitting} onClick={() => void submit()}>
              {submitting ? "创建中…" : "确认创建"}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
