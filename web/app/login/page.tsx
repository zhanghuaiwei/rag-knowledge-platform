"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { Alert, Button, Divider, Form, Input } from "antd";
import { ApartmentOutlined, LockOutlined, UserOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import { isAuthed } from "@/lib/auth";

/**
 * 表单登录：登录标识（用户名/邮箱）+ 密码，能否登录由后端数据库（user_credential/sys_user）决定，
 * 不开放自助注册。企业部署可走 OIDC（SSO 按钮跳后端授权端点），由 IdP 承载认证。
 * 登录成功后由 api.login 持有 access token（内存），refresh 凭证走 HttpOnly cookie。
 * ⚠️ 不在前端硬编码任何账号/口令（本地账号凭据只在数据库中维护）。
 */

interface LoginFormValues {
  username: string;
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
    void login(values.username.trim(), values.password);
  };

  // 企业 SSO：跳后端 OIDC 授权端点（仅 oidc 模式开放；form 模式后端不提供该端点）
  const handleSsoLogin = () => {
    window.location.href = "/api/v1/auth/authorize";
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
            name="username"
            label="登录账号 / 邮箱"
            rules={[
              { required: true, message: "请输入登录账号或邮箱" },
            ]}
          >
            <Input size="large" autoComplete="username" placeholder="用户名或邮箱" prefix={<UserOutlined />} />
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

        <Button block size="large" icon={<ApartmentOutlined />} disabled={loading} onClick={handleSsoLogin}>
          使用企业 SSO 登录
        </Button>

        <p style={{ fontSize: 12, color: "var(--text-3)", marginTop: 18 }}>
          登录账号由管理员开通，平台不开放自助注册。
          <br />
          本地/私有化部署的引导账号见部署运维文档，生产环境走企业 IdP 认证
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
