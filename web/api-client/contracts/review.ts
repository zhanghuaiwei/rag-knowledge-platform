import type { PageParams, PageResult, ReviewItem } from "@/api-client/types";

/** 内容审核契约（F2.13）。 */
export interface ReviewApi {
  listReviews(params?: PageParams): Promise<PageResult<ReviewItem>>;
  approveReviews(ids: number[], comment?: string): Promise<void>;
  rejectReviews(ids: number[], comment: string): Promise<void>;
}
