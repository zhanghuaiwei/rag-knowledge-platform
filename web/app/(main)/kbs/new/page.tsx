"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

import { Icon } from "@/components/icons";
import { Switch, useToast } from "@/components/ui";

const STEPS = ["基本信息", "归属与治理", "策略与配额", "确认创建"] as const;

interface WizardForm {
  name: string;
  description: string;
  visibility: "PRIVATE" | "TENANT";
  owner: string;
  domain: string;
  requiresReview: boolean;
  sensitivity: string;
  retention: string;
  dataRegion: string;
  modelPolicy: string;
  ocrEnabled: boolean;
}

const INITIAL: WizardForm = {
  name: "",
  description: "",
  visibility: "PRIVATE",
  owner: "张伟（当前用户）",
  domain: "",
  requiresReview: true,
  sensitivity: "INTERNAL",
  retention: "3 年",
  dataRegion: "华东（上海）",
  modelPolicy: "仅境内模型",
  ocrEnabled: false,
};

export default function NewKbPage() {
  const router = useRouter();
  const toast = useToast();
  const [step, setStep] = useState(0);
  const [form, setForm] = useState<WizardForm>(INITIAL);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const set = <K extends keyof WizardForm>(key: K, value: WizardForm[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: "" }));
  };

  const validateStep = (): boolean => {
    const next: Record<string, string> = {};
    if (step === 0) {
      if (!form.name.trim()) next.name = "请输入知识库名称";
      else if (form.name.trim().length < 2) next.name = "名称至少 2 个字符";
      if (form.description.length > 200) next.description = "描述不超过 200 字";
    }
    if (step === 1 && !form.domain) next.domain = "请选择业务域";
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const nextStep = () => {
    if (!validateStep()) return;
    setStep((s) => Math.min(s + 1, STEPS.length - 1));
  };

  const submit = () => {
    setSubmitting(true);
    // mock：真实实现调用创建知识库接口（契约待冻结）
    setTimeout(() => {
      toast("success", `知识库「${form.name}」已创建（mock）`);
      router.push("/kbs");
    }, 700);
  };

  return (
    <div className="page" style={{ maxWidth: 860, margin: "0 auto" }}>
      <div className="page-header">
        <div>
          <h1 className="page-title">新建知识库</h1>
          <p className="page-desc">治理与策略在创建时确定，后续可在设置中调整</p>
        </div>
        <button className="btn btn-ghost" onClick={() => router.back()}>
          <Icon name="arrow-left" size={15} /> 返回
        </button>
      </div>

      <div className="card">
        <div className="steps">
          {STEPS.map((label, i) => (
            <div key={label} className={`step${i < step ? " done" : ""}${i === step ? " current" : ""}`}>
              <span className="step-dot">{i < step ? <Icon name="check" size={14} /> : i + 1}</span>
              <span className="step-label">{label}</span>
              {i < STEPS.length - 1 ? <span className="step-line" /> : null}
            </div>
          ))}
        </div>

        {step === 0 ? (
          <>
            <div className="field">
              <label className="field-label">知识库名称<span className="req">*</span></label>
              <input className="input" placeholder="例如：产品研发知识库" value={form.name} onChange={(e) => set("name", e.target.value)} />
              {errors.name ? <div className="field-error">{errors.name}</div> : null}
            </div>
            <div className="field">
              <label className="field-label">描述</label>
              <textarea className="textarea" placeholder="说明知识范围、适用人群与维护责任…" value={form.description} onChange={(e) => set("description", e.target.value)} />
              {errors.description ? <div className="field-error">{errors.description}</div> : <div className="field-hint">{form.description.length}/200</div>}
            </div>
            <div className="field" style={{ marginBottom: 0 }}>
              <label className="field-label">可见性</label>
              <div className="seg">
                <button className={`seg-item${form.visibility === "PRIVATE" ? " active" : ""}`} onClick={() => set("visibility", "PRIVATE")}>私有（仅成员）</button>
                <button className={`seg-item${form.visibility === "TENANT" ? " active" : ""}`} onClick={() => set("visibility", "TENANT")}>租户内可见</button>
              </div>
            </div>
          </>
        ) : null}

        {step === 1 ? (
          <>
            <div className="grid grid-2">
              <div className="field">
                <label className="field-label">所有者</label>
                <input className="input" value={form.owner} onChange={(e) => set("owner", e.target.value)} />
              </div>
              <div className="field">
                <label className="field-label">业务域<span className="req">*</span></label>
                <select className="select" value={form.domain} onChange={(e) => set("domain", e.target.value)}>
                  <option value="">请选择</option>
                  <option>产品研发</option>
                  <option>市场营销</option>
                  <option>人力资源</option>
                  <option>财务合规</option>
                  <option>客户服务</option>
                </select>
                {errors.domain ? <div className="field-error">{errors.domain}</div> : null}
              </div>
              <div className="field">
                <label className="field-label">默认敏感级</label>
                <select className="select" value={form.sensitivity} onChange={(e) => set("sensitivity", e.target.value)}>
                  <option value="PUBLIC">公开</option>
                  <option value="INTERNAL">内部</option>
                  <option value="CONFIDENTIAL">机密</option>
                  <option value="RESTRICTED">绝密</option>
                </select>
              </div>
              <div className="field">
                <label className="field-label">保留策略</label>
                <select className="select" value={form.retention} onChange={(e) => set("retention", e.target.value)}>
                  <option>1 年</option>
                  <option>3 年</option>
                  <option>5 年</option>
                  <option>永久保留</option>
                </select>
              </div>
            </div>
            <div className="setting-row">
              <div>
                <div className="setting-label">发布前审核</div>
                <div className="setting-desc">文档解析就绪后需审核通过才可被检索</div>
              </div>
              <Switch checked={form.requiresReview} onChange={(v) => set("requiresReview", v)} />
            </div>
            <div className="setting-row">
              <div>
                <div className="setting-label">启用 OCR</div>
                <div className="setting-desc">扫描件与图片型 PDF 提取文字</div>
              </div>
              <Switch checked={form.ocrEnabled} onChange={(v) => set("ocrEnabled", v)} />
            </div>
          </>
        ) : null}

        {step === 2 ? (
          <>
            <div className="grid grid-2">
              <div className="field">
                <label className="field-label">数据区域</label>
                <select className="select" value={form.dataRegion} onChange={(e) => set("dataRegion", e.target.value)}>
                  <option>华东（上海）</option>
                  <option>华北（北京）</option>
                  <option>华南（深圳）</option>
                </select>
                <div className="field-hint">高敏内容不会路由到区域外的模型</div>
              </div>
              <div className="field">
                <label className="field-label">模型路由策略</label>
                <select className="select" value={form.modelPolicy} onChange={(e) => set("modelPolicy", e.target.value)}>
                  <option>仅境内模型</option>
                  <option>仅私有化模型</option>
                  <option>按敏感级自动路由</option>
                </select>
              </div>
            </div>
            <div className="card" style={{ background: "var(--surface-2)", boxShadow: "none" }}>
              <h4 style={{ marginBottom: 10 }}>配额摘要</h4>
              <dl className="kv">
                <dt>存储上限</dt><dd>50 GB（租户剩余 320 GB）</dd>
                <dt>文档上限</dt><dd>10,000 篇</dd>
                <dt>索引 Profile</dt><dd>standard-1024（创建后不可原地修改，换模走索引重建）</dd>
              </dl>
            </div>
          </>
        ) : null}

        {step === 3 ? (
          <div>
            <h4 style={{ marginBottom: 12 }}>请确认以下配置</h4>
            <dl className="kv card" style={{ background: "var(--surface-2)", boxShadow: "none" }}>
              <dt>名称</dt><dd>{form.name}</dd>
              <dt>可见性</dt><dd>{form.visibility === "PRIVATE" ? "私有（仅成员）" : "租户内可见"}</dd>
              <dt>所有者 / 业务域</dt><dd>{form.owner} · {form.domain}</dd>
              <dt>治理</dt><dd>默认敏感级 {form.sensitivity} · 保留 {form.retention} · {form.requiresReview ? "发布前审核" : "免审核"}{form.ocrEnabled ? " · OCR" : ""}</dd>
              <dt>策略</dt><dd>{form.dataRegion} · {form.modelPolicy}</dd>
            </dl>
            <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 12 }}>
              创建后索引 Profile 绑定为不可变版本；换模仅通过「新索引全量重建 → 校验 → 别名原子切换」完成。
            </p>
          </div>
        ) : null}

        <div style={{ display: "flex", justifyContent: "space-between", marginTop: 24 }}>
          <button className="btn" onClick={() => setStep((s) => Math.max(s - 1, 0))} disabled={step === 0 || submitting}>
            上一步
          </button>
          {step < STEPS.length - 1 ? (
            <button className="btn btn-primary" onClick={nextStep}>下一步</button>
          ) : (
            <button className="btn btn-primary" onClick={submit} disabled={submitting}>
              {submitting ? "创建中…" : "确认创建"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
