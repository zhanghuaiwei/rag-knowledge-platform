import type { PageResult, SearchItem, SearchParams } from "@/api-client/types";

/** 全文搜索契约（F2.9）。 */
export interface SearchApi {
  search(params: SearchParams): Promise<PageResult<SearchItem>>;
}
