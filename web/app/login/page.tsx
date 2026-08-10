"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";

import { Icon } from "@/components/icons";
import { isAuthed, setSession } from "@/lib/auth";

/** mock 演示账号：真实环境走 OIDC，由企业 IdP 承载认证与 MFA。 */
const DEMO_ACCOUNT = { email: "admin@ragkb.dev", password: "admin123" };

function LoginPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const from = searchParams.get("from");
  const target = from && from.startsWith("/") && !from.startsWith("//") ? from : "/dashboard";

  // 已登录直接进工作台
  useEffect(() => {
    if (isAuthed()) router.replace("/dashboard");
  }, [router]);

  const login = (loginEmail: string) => {
    setSession(loginEmail);
    router.replace(target);
  };

  const submit = () => {
    const trimmed = email.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmed)) {
      setError("请输入有效的企业邮箱");
      return;
    }
    if (password.length < 6) {
      setError("密码至少 6 位");
      return;
    }
    setError("");
    setLoading(true);
    // mock 校验：演示账号或任意「域邮箱 + 6 位以上密码」均可进入
    setTimeout(() => login(trimmed), 500);
  };

  return (
    <div className="login-page">
      <div className="card login-card">
        <div className="brand-logo login-logo">知</div>
        <h1 style={{ fontSize: 22 }}>通用企业知识库平台</h1>
        <p style={{ color: "var(--text-2)", margin: "8px 0 24px" }}>
          问答 · 搜索 · 治理 · 运营，权限正确且可追溯
        </p>

        <form
          style={{ textAlign: "left" }}
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
        >
          <div className="field">
            <label className="field-label" htmlFor="login-email">企业邮箱</label>
            <input
              id="login-email"
              className="input"
              type="email"
              autoComplete="username"
              placeholder="name@company.com"
              value={email}
              onChange={(e) => { setEmail(e.target.value); setError(""); }}
            />
          </div>
          <div className="field">
            <label className="field-label" htmlFor="login-password">密码</label>
            <input
              id="login-password"
              className="input"
              type="password"
              autoComplete="current-password"
              placeholder="至少 6 位"
              value={password}
              onChange={(e) => { setPassword(e.target.value); setError(""); }}
              onKeyDown={(e) => e.key === "Enter" && submit()}
            />
          </div>
          {error ? <p className="field-error" style={{ marginBottom: 12 }}>{error}</p> : null}
          <button type="submit" className="btn btn-primary btn-lg btn-block" disabled={loading}>
            {loading ? "登录中…" : "登录"}
          </button>
        </form>

        <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "16px 0", color: "var(--text-3)", fontSize: 12 }}>
          <span style={{ flex: 1, height: 1, background: "var(--border)" }} />
          或
          <span style={{ flex: 1, height: 1, background: "var(--border)" }} />
        </div>
        <button className="btn btn-block" onClick={() => login(DEMO_ACCOUNT.email)} disabled={loading}>
          <Icon name="building" size={16} /> 使用企业 SSO 登录
        </button>

        <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 18 }}>
          演示账号：{DEMO_ACCOUNT.email} / {DEMO_ACCOUNT.password}（任意邮箱 + 6 位以上密码亦可）
          <br />
          无账号请联系管理员邀请开通 · 生产环境为 OIDC Authorization Code + PKCE
        </p>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginPageInner />
    </Suspense>
  );
}
