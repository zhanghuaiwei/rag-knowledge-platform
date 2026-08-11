/**
 * 域契约组合成统一 ApiClient 接口（与 OpenAPI 对齐）。
 * mock transport 与 http transport 都按本接口实现，页面只依赖 `api`。
 */
import type { AuthApi } from "@/api-client/contracts/auth";
import type { KbApi } from "@/api-client/contracts/kb";
import type { DocumentApi } from "@/api-client/contracts/document";
import type { ReviewApi } from "@/api-client/contracts/review";
import type { AdminApi } from "@/api-client/contracts/admin";
import type { GovernanceApi } from "@/api-client/contracts/governance";
import type { ChatApi } from "@/api-client/contracts/chat";
import type { SearchApi } from "@/api-client/contracts/search";
import type { AnalyticsApi } from "@/api-client/contracts/analytics";
import type { MiscApi } from "@/api-client/contracts/misc";

export interface ApiClient
  extends AuthApi,
    KbApi,
    DocumentApi,
    ReviewApi,
    AdminApi,
    GovernanceApi,
    ChatApi,
    SearchApi,
    AnalyticsApi,
    MiscApi {}
