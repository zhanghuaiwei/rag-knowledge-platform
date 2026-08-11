/**
 * 真实 HTTP transport：按域模块组合成统一 ApiClient 实现。
 *
 * 与 mock transport 形状完全一致（同属 ApiClient 接口）；由 client.ts 依据
 * `NEXT_PUBLIC_USE_MOCK` 切换。页面只依赖 api-client，不感知数据来源。
 */
import type { ApiClient } from "@/api-client/contracts";
import { adminApi } from "@/api-client/http/admin";
import { analyticsApi } from "@/api-client/http/analytics";
import { authApi } from "@/api-client/http/auth";
import { chatApi } from "@/api-client/http/chat";
import { documentApi } from "@/api-client/http/document";
import { governanceApi } from "@/api-client/http/governance";
import { kbApi } from "@/api-client/http/kb";
import { miscApi } from "@/api-client/http/misc";
import { reviewApi } from "@/api-client/http/review";
import { searchApi } from "@/api-client/http/search";

export const httpClient: ApiClient = {
  ...authApi,
  ...kbApi,
  ...documentApi,
  ...reviewApi,
  ...adminApi,
  ...governanceApi,
  ...chatApi,
  ...searchApi,
  ...analyticsApi,
  ...miscApi,
};

export { ApiError } from "@/api-client/http/errors";
export { buildApiUrl, request, requestBlob, requestVoid, waitForTask } from "@/api-client/http/client";
export { readSse } from "@/api-client/http/sse";
