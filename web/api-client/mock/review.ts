import type { ReviewApi } from "@/api-client/contracts/review";
import type { PageParams } from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";
import { appendAudit } from "@/mocks/helpers";

/** 审核动作传播到文档 reviewStatus：通过→PUBLISHED，驳回→REJECTED，并从队列移除。 */
function applyReview(ids: number[], status: "PUBLISHED" | "REJECTED", comment?: string): void {
  for (const id of ids) {
    const detail = db.getDocumentDetail(id);
    if (!detail) continue;
    detail.reviewStatus = status;
    detail.updatedAt = new Date().toISOString();
    db.updateDocument(id, detail);
    appendAudit({
      action: status === "PUBLISHED" ? "review.approve" : "review.reject",
      resourceType: "DOCUMENT",
      resourceId: id,
      reasonCode: comment ? "COMMENTED" : null,
    });
  }
  const keep = db.reviewItems.filter((item) => !ids.includes(item.documentId));
  db.reviewItems.splice(0, db.reviewItems.length, ...keep);
}

export const reviewApi: ReviewApi = {
  async listReviews(params: PageParams = {}) {
    await delay();
    return paginate(db.reviewItems, params.page, params.size);
  },

  async approveReviews(ids: number[], comment?: string) {
    await delay(400);
    applyReview(ids, "PUBLISHED", comment);
  },

  async rejectReviews(ids: number[], comment: string) {
    await delay(400);
    if (!comment.trim()) throw new Error("驳回必须填写审核意见");
    applyReview(ids, "REJECTED", comment);
  },
};
