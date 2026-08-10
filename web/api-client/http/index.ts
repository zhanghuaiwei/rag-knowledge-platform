/**
 * 真实 HTTP transport（占位）。
 *
 * 当前 OpenAPI v0.2 仍处于评审冻结阶段（docs/api/server.openapi.yaml），
 * 端点与字段未最终确定，因此不提前实现 fetch 逻辑，避免与权威契约失配。
 *
 * 冻结后在本模块基于 API base URL 实现各方法，并替换 client.ts 中的
 * `httpClient`；页面代码无需改动（只依赖 ApiClient 接口）。
 */
import type { ApiClient } from "@/api-client/client";

function notImplemented(name: string): never {
  throw new Error(
    `[api-client] http transport 的 ${name} 尚未实现：OpenAPI v0.2 冻结后接入。` +
      "当前请使用内置 mock（默认开启，无需后端）。",
  );
}

/**
 * 通过 Proxy 生成同接口占位实现：任何方法被调用都会给出明确指引，
 * 避免逐一手写占位方法，也保证接口形状与 mock 完全一致。
 */
export const httpClient: ApiClient = new Proxy({} as ApiClient, {
  get(_target, prop) {
    return () => notImplemented(String(prop));
  },
});
