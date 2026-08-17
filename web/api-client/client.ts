/**
 * 统一 API 客户端：固定使用真实 HTTP transport。
 *
 * 页面只依赖本模块的 `api`，禁止组件直接 fetch 或拼接完整请求地址。
 * 接口形状由 contracts/ 定义，真实 HTTP 实现见 http/。
 */
import { httpClient } from "@/api-client/http";
import type { ApiClient } from "@/api-client/contracts";

/** 唯一客户端实例。 */
export const api: ApiClient = httpClient;

export type { ApiClient } from "@/api-client/contracts";
