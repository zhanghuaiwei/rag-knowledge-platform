/**
 * mock 删除任务与删除证明（GKB-04）。
 * 删除为逻辑删除：先审批，再传播到对象存储/索引/缓存/备份各副本，最后生成可校验证明。
 */
import type { DeletionReceipt, DeletionTask } from "@/api-client/types";

export const deletionTasks: DeletionTask[] = [
  {
    id: 1,
    documentId: 6,
    fileName: "第三方SDK采购清单.xlsx",
    reason: "采购清单已过期，业务方申请清除",
    requestedBy: "王建国",
    status: "PENDING_APPROVAL",
    createdAt: "2026-08-10T02:05:00Z",
    progress: { storage: false, index: false, cache: false, backup: false },
  },
  {
    id: 2,
    documentId: 4,
    fileName: "缓存一致性方案评审.pdf",
    reason: "评审结论作废，内容已被替代文档覆盖",
    requestedBy: "李佳宁",
    status: "RUNNING",
    createdAt: "2026-08-09T08:00:00Z",
    progress: { storage: true, index: true, cache: false, backup: false },
  },
];

export const deletionReceipts: DeletionReceipt[] = [
  {
    id: 1,
    taskId: 3,
    documentId: 18,
    fileName: "旧版接口规范.docx",
    checksum: "sha256:9f2c…41ab",
    deletedAt: "2026-08-08T02:00:00Z",
    operator: "张怀伟",
  },
];
