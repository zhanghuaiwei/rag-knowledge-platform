import { NextResponse } from "next/server";

/**
 * 探针端点：验证应用可构建、可访问。
 * 生产健康/存活由部署侧探针与 server Actuator 负责（06-架构方案）。
 */
export function GET() {
  return NextResponse.json({
    status: "ok",
    service: "ragkb-web",
    // 当前阶段：MVP（页面已接真实 HTTP transport，mock 层已移除）
    phase: "mvp",
  });
}
