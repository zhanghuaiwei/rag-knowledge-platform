/**
 * 全文搜索域真实 HTTP transport（对齐 OpenAPI search，游标分页）。
 * 前端契约用 page/size，后端用 cursor：此处返回首页并按需映射为 PageResult。
 */
import type { SearchApi } from "@/api-client/contracts/search";
import type { PageResult, SearchItem, SearchParams } from "@/api-client/types";
import { request } from "@/api-client/http/client";

interface CursorSearchResult {
  items?: SearchItem[];
  nextCursor?: string | null;
  hasMore?: boolean;
}

export const searchApi: SearchApi = {
  async search(params: SearchParams) {
    const result = await request<CursorSearchResult>({
      method: "GET",
      url: "/search",
      params: {
        q: params.keyword,
        kbIds: params.kbIds && params.kbIds.length > 0 ? params.kbIds : undefined,
        fileExts: params.fileType ? [params.fileType] : undefined,
        dateFrom: params.dateFrom,
        dateTo: params.dateTo,
        size: params.size ?? 20,
      },
    });
    const items = result.items ?? [];
    return {
      items,
      total: items.length,
      page: params.page ?? 1,
      size: params.size ?? items.length,
      hasMore: result.hasMore ?? false,
    } satisfies PageResult<SearchItem>;
  },
};
