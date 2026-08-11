/**
 * 统一 API 客户端：按环境变量自动选择 transport。
 *
 * - 默认（NEXT_PUBLIC_USE_MOCK=true 或未设置）：使用内置 mock 数据，脱离后端可演示。
 * - NEXT_PUBLIC_USE_MOCK=false：使用真实 HTTP transport（OpenAPI v0.2 冻结后接入）。
 *
 * 页面只依赖本模块的 `api`，禁止组件直接 fetch 或拼接完整请求地址。
 * 接口形状由 contracts/ 定义，mock 与 http 双实现保持一致。
 */
import { httpClient } from "@/api-client/http";
import { mockClient } from "@/api-client/mock";
import type { ApiClient } from "@/api-client/contracts";

const useMock = process.env.NEXT_PUBLIC_USE_MOCK !== "false";

/** 唯一客户端实例。 */
export const api: ApiClient = useMock ? mockClient : httpClient;

export type { ApiClient } from "@/api-client/contracts";
