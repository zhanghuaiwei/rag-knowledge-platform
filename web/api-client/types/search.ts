/** 全文搜索域类型（F2.9）。 */
import type { PageParams } from "@/api-client/types/common";

export type SearchSort = "RELEVANCE" | "TIME";

export interface SearchParams extends PageParams {
  keyword: string;
  kbIds?: number[];
  dateFrom?: string;
  dateTo?: string;
  fileType?: string;
  sort?: SearchSort;
}

export interface SearchItem {
  documentId: number;
  fileName: string;
  kbId: number;
  pageNo: number;
  sectionTitle: string;
  fileExt: string;
  snippet: string;
  score: number;
  updatedAt: string;
}
