/**
 * 内容审核域真实 HTTP transport（对齐 OpenAPI governance reviews）。
 * 前端批量审批：逐条调用审批/驳回端点。
 */
import type { ReviewApi } from "@/api-client/contracts/review";
import type { PageParams, PageResult, ReviewItem } from "@/api-client/types";
import { request, requestVoid } from "@/api-client/http/client";

export const reviewApi: ReviewApi = {
  async listReviews(params: PageParams = {}) {
    return request<PageResult<ReviewItem>>({
      method: "GET",
      url: "/reviews",
      params: { page: params.page ?? 1, size: params.size ?? 20 },
    });
  },

  async approveReviews(ids: number[], comment?: string) {
    await Promise.all(
      ids.map((id) =>
        requestVoid({
          method: "POST",
          url: `/reviews/${id}/approve`,
          data: { comment },
        }),
      ),
    );
  },

  async rejectReviews(ids: number[], comment: string) {
    await Promise.all(
      ids.map((id) =>
        requestVoid({
          method: "POST",
          url: `/reviews/${id}/reject`,
          data: { comment },
        }),
      ),
    );
  },
};
