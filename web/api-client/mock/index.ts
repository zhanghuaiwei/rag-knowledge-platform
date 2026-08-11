/**
 * mock transport：按域模块组合成统一 ApiClient 实现，从 mocks/db 读写数据。
 *
 * 页面与组件通过 api-client 消费，不感知数据来自 mock 还是真实后端。
 * 数据仅用于本地开发与演示，不代表真实验收证据。
 */
import type { ApiClient } from "@/api-client/contracts";
import { adminApi } from "@/api-client/mock/admin";
import { analyticsApi } from "@/api-client/mock/analytics";
import { authApi } from "@/api-client/mock/auth";
import { chatApi } from "@/api-client/mock/chat";
import { documentApi } from "@/api-client/mock/document";
import { governanceApi } from "@/api-client/mock/governance";
import { kbApi } from "@/api-client/mock/kb";
import { miscApi } from "@/api-client/mock/misc";
import { reviewApi } from "@/api-client/mock/review";
import { searchApi } from "@/api-client/mock/search";

export const mockClient: ApiClient = {
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
