"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Alert, Button, Divider, Form, Input } from "antd";
import { ApartmentOutlined, LockOutlined, MailOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import { isAuthed } from "@/lib/auth";

/**
 * 开发/演示账号：开发阶段保留表单登录，供本地与演示环境使用。
 * 生产环境走 OIDC，由企业 IdP 承载认证与 MFA，表单登录不暴露。
 * 登录成功后由 api.login 持有 access token（内存），refresh 凭证走 HttpOnly cookie。
 */
const DEV_ACCOUNTS = [
  { email: "admin@ragkb.dev", password: "admin123", role: "管理员" },
  { email: "user@ragkb.dev", password: "user123", role: "普通成员" },
];
const DEFAULT_DEV_ACCOUNT = DEV_ACCOUNTS[0];

interface LoginFormValues {
  email: string;
  password: string;
}

function LoginPageInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const from = searchParams.get("from");
  const target = from && from.startsWith("/") && !from.startsWith("//") ? from : "/dashboard";

  // 已登录（内存 access token 有效）直接进工作台
  useEffect(() => {
    if (isAuthed()) router.replace("/dashboard");
  }, [router]);

  const login = async (username: string, password: string) => {
    setLoading(true);
    setError(null);
    try {
      await api.login({ username, password });
      router.replace(target);
    } catch (err) {
      setError(err instanceof Error ? err.message : "登录失败，请稍后重试");
      setLoading(false);
    }
  };

  const onFinish = (values: LoginFormValues) => {
    void login(values.email.trim(), values.password);
  };

  return (
    <div className="login-page">
      <div className="card login-card">
        <div className="brand-logo login-logo">知</div>
        <h1 style={{ fontSize: 22 }}>通用企业知识库平台</h1>
        <p style={{ color: "var(--text-2)", margin: "8px 0 24px" }}>
          问答 · 搜索 · 治理 · 运营，权限正确且可追溯
        </p>

        {error ? <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} /> : null}

        <Form<LoginFormValues>
          layout="vertical"
          requiredMark={false}
          onFinish={onFinish}
        >
          <Form.Item
            name="email"
            label="企业邮箱"
            rules={[
              { required: true, message: "请输入企业邮箱" },
              { type: "email", message: "请输入有效的企业邮箱" },
            ]}
          >
            <Input size="large" autoComplete="username" placeholder="name@company.com" prefix={<MailOutlined />} />
          </Form.Item>
          <Form.Item
            name="password"
            label="密码"
            rules={[
              { required: true, message: "请输入密码" },
              { min: 6, message: "密码至少 6 位" },
            ]}
          >
            <Input.Password size="large" autoComplete="current-password" placeholder="至少 6 位" prefix={<LockOutlined />} />
          </Form.Item>
          <Form.Item style={{ marginBottom: 12 }}>
            <Button type="primary" htmlType="submit" size="large" block loading={loading} disabled={loading}>
              {loading ? "登录中…" : "登录"}
            </Button>
          </Form.Item>
        </Form>

        <Divider plain>或</Divider>

        <Button
          block
          size="large"
          icon={<ApartmentOutlined />}
          disabled={loading}
          onClick={() => void login(DEFAULT_DEV_ACCOUNT.email, DEFAULT_DEV_ACCOUNT.password)}
        >
          使用企业 SSO 登录
        </Button>

        <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 18 }}>
          开发账号（开发/演示环境生效，生产环境走 OIDC）：
          {DEV_ACCOUNTS.map((a) => `${a.email} / ${a.password}（${a.role}）`).join(" · ")}
          <br />
          企业平台不开放自助注册，成员由管理员邀请开通
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
