"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button, Card, Form, Input } from "antd";
import { KeyOutlined, LockOutlined } from "@ant-design/icons";

import { api } from "@/api-client";
import { useAuth } from "@/components/auth-provider";
import { useToast } from "@/components/feedback";

interface ChangePasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/**
 * V0.5 本地账号：首登/被重置后强制改密页。
 * 服务端（CredentialPolicyGateFilter）除白名单路径外一律 403 引导至此；
 * 改密成功后清除 mustChangePassword 标志并回到工作台。
 */
export default function ChangePasswordPage() {
  const router = useRouter();
  const toast = useToast();
  const { refresh } = useAuth();
  const [submitting, setSubmitting] = useState(false);

  const onFinish = async (values: ChangePasswordForm) => {
    if (values.newPassword !== values.confirmPassword) {
      toast("error", "两次输入的新密码不一致");
      return;
    }
    setSubmitting(true);
    try {
      await api.changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      await refresh(); // 会话标志经 DB 重读清除
      toast("success", "密码修改成功");
      router.replace("/dashboard");
    } catch (err: unknown) {
      toast("error", err instanceof Error ? err.message : "修改失败");
      setSubmitting(false);
    }
  };

  return (
    <div className="page" style={{ maxWidth: 480, margin: "40px auto" }}>
      <Card>
        <div style={{ textAlign: "center", marginBottom: 24 }}>
          <div style={{ fontSize: 34 }}>🔐</div>
          <h1 style={{ fontSize: 20, margin: "8px 0 4px" }}>设置新密码</h1>
          <p style={{ color: "var(--text-2)", fontSize: 13 }}>
            首次登录或管理员重置后需先修改初始密码，之后才能正常使用平台
          </p>
        </div>
        <Form<ChangePasswordForm> layout="vertical" requiredMark={false} onFinish={onFinish}>
          <Form.Item
            name="currentPassword"
            label="当前密码"
            rules={[{ required: true, message: "请输入当前密码" }]}
          >
            <Input.Password autoComplete="current-password" prefix={<KeyOutlined />} placeholder="当前密码" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: "请输入新密码" },
              { min: 6, message: "密码至少 6 位" },
            ]}
          >
            <Input.Password autoComplete="new-password" prefix={<LockOutlined />} placeholder="至少 6 位" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={["newPassword"]}
            rules={[
              { required: true, message: "请再次输入新密码" },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue("newPassword") === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error("两次输入的新密码不一致"));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" prefix={<LockOutlined />} placeholder="再次输入新密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={submitting} disabled={submitting}>
            确认修改
          </Button>
        </Form>
      </Card>
    </div>
  );
}
