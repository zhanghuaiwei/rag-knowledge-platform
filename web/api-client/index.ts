/**
 * api-client 统一出口。
 *
 * 页面只从这里导入 `api` 与类型；请求地址与协议细节只在 api-client 内部出现，
 * 禁止组件直接 fetch（AGENTS.md 开发红线）。
 */
export { api } from "@/api-client/client";
export type { ApiClient } from "@/api-client/client";
export * from "@/api-client/types";
