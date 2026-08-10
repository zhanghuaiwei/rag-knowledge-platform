/**
 * mock 文档数据（对齐 document / document_version 语义）。
 * 覆盖不同来源、解析状态、审核状态与敏感级，供列表/详情/审核页演示。
 */
import type {
  DocumentDetail,
  DocumentSummary,
  DocumentVersion,
} from "@/api-client/types";

export const documentVersions: Record<number, DocumentVersion[]> = {
  1: [
    { versionNo: 2, fileSize: 2_412_000, ingestStatus: "READY", safetyStatus: "PASSED", chunkCount: 42, createdBy: "李佳宁", createdAt: "2026-08-08T06:10:00Z" },
    { versionNo: 1, fileSize: 2_300_000, ingestStatus: "READY", safetyStatus: "PASSED", chunkCount: 38, createdBy: "李佳宁", createdAt: "2026-07-21T03:00:00Z" },
  ],
  2: [
    { versionNo: 1, fileSize: 980_000, ingestStatus: "READY", safetyStatus: "PASSED", chunkCount: 31, createdBy: "王建国", createdAt: "2026-07-30T09:20:00Z" },
  ],
  3: [
    { versionNo: 1, fileSize: 1_350_000, ingestStatus: "PARSING", safetyStatus: "PASSED", chunkCount: 0, createdBy: "张怀伟", createdAt: "2026-08-10T00:30:00Z" },
  ],
  4: [
    { versionNo: 1, fileSize: 820_000, ingestStatus: "FAILED", safetyStatus: "PASSED", chunkCount: 0, createdBy: "李佳宁", createdAt: "2026-08-09T08:00:00Z" },
  ],
  5: [
    { versionNo: 1, fileSize: 4_050_000, ingestStatus: "READY", safetyStatus: "PASSED", chunkCount: 67, createdBy: "张怀伟", createdAt: "2026-08-01T02:30:00Z" },
  ],
  6: [
    { versionNo: 1, fileSize: 640_000, ingestStatus: "QUARANTINED", safetyStatus: "PENDING", chunkCount: 0, createdBy: "王建国", createdAt: "2026-08-10T02:05:00Z" },
  ],
};

export const documents: DocumentSummary[] = [
  { id: 1, kbId: 1, kbName: "产品研发知识库", title: "微服务架构设计规范", fileName: "微服务架构设计规范.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "UPLOAD", fileSize: 2_412_000, versionNo: 2, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "INTERNAL", ownerName: "李佳宁", chunkCount: 42, updatedAt: "2026-08-08T06:10:00Z" },
  { id: 2, kbId: 1, kbName: "产品研发知识库", title: "灰度发布与回滚实践", fileName: "灰度发布与回滚实践.docx", fileExt: "docx", mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sourceType: "UPLOAD", fileSize: 980_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "INTERNAL", ownerName: "王建国", chunkCount: 31, updatedAt: "2026-07-30T09:20:00Z" },
  { id: 3, kbId: 1, kbName: "产品研发知识库", title: "OpenTelemetry 接入指南", fileName: "OpenTelemetry接入指南.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "CONNECTOR", fileSize: 1_350_000, versionNo: 1, ingestStatus: "PARSING", reviewStatus: "PENDING_REVIEW", sensitivity: "INTERNAL", ownerName: "张怀伟", chunkCount: 0, updatedAt: "2026-08-10T00:30:00Z" },
  { id: 4, kbId: 1, kbName: "产品研发知识库", title: "缓存一致性方案评审", fileName: "缓存一致性方案评审.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "UPLOAD", fileSize: 820_000, versionNo: 1, ingestStatus: "FAILED", reviewStatus: "DRAFT", sensitivity: "CONFIDENTIAL", ownerName: "李佳宁", chunkCount: 0, updatedAt: "2026-08-09T08:00:00Z" },
  { id: 5, kbId: 1, kbName: "产品研发知识库", title: "分布式事务模式选型", fileName: "分布式事务模式选型.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "WEB", fileSize: 4_050_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "INTERNAL", ownerName: "张怀伟", chunkCount: 67, updatedAt: "2026-08-01T02:30:00Z" },
  { id: 6, kbId: 1, kbName: "产品研发知识库", title: "第三方 SDK 采购清单", fileName: "第三方SDK采购清单.xlsx", fileExt: "xlsx", mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", sourceType: "UPLOAD", fileSize: 640_000, versionNo: 1, ingestStatus: "QUARANTINED", reviewStatus: "DRAFT", sensitivity: "CONFIDENTIAL", ownerName: "王建国", chunkCount: 0, updatedAt: "2026-08-10T02:05:00Z" },
  { id: 7, kbId: 2, kbName: "后端技术规范库", title: "Java 编码规范", fileName: "Java编码规范.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "UPLOAD", fileSize: 3_120_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "PUBLIC", ownerName: "张怀伟", chunkCount: 88, updatedAt: "2026-08-06T07:40:00Z" },
  { id: 8, kbId: 2, kbName: "后端技术规范库", title: "SQL 与数据库规范", fileName: "SQL与数据库规范.md", fileExt: "md", mimeType: "text/markdown", sourceType: "UPLOAD", fileSize: 215_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "INTERNAL", ownerName: "李佳宁", chunkCount: 45, updatedAt: "2026-08-05T03:20:00Z" },
  { id: 9, kbId: 2, kbName: "后端技术规范库", title: "接口幂等设计规范", fileName: "接口幂等设计规范.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "UPLOAD", fileSize: 1_100_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PENDING_REVIEW", sensitivity: "INTERNAL", ownerName: "王建国", chunkCount: 29, updatedAt: "2026-08-04T05:00:00Z" },
  { id: 10, kbId: 2, kbName: "后端技术规范库", title: "日志与可观测性规范", fileName: "日志与可观测性规范.docx", fileExt: "docx", mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sourceType: "UPLOAD", fileSize: 860_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "PUBLIC", ownerName: "张怀伟", chunkCount: 36, updatedAt: "2026-08-03T01:10:00Z" },
  { id: 11, kbId: 3, kbName: "客户成功手册", title: "工单处理 SOP", fileName: "工单处理SOP.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "UPLOAD", fileSize: 2_050_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "INTERNAL", ownerName: "周雨桐", chunkCount: 54, updatedAt: "2026-08-02T08:30:00Z" },
  { id: 12, kbId: 3, kbName: "客户成功手册", title: "常见问题 FAQ", fileName: "常见问题FAQ.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "CONNECTOR", fileSize: 1_400_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "PUBLIC", ownerName: "陈晓芸", chunkCount: 48, updatedAt: "2026-08-01T04:15:00Z" },
  { id: 13, kbId: 4, kbName: "销售与方案库", title: "产品白皮书 v3", fileName: "产品白皮书v3.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "UPLOAD", fileSize: 8_200_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "CONFIDENTIAL", ownerName: "刘思彤", chunkCount: 96, updatedAt: "2026-07-25T06:00:00Z" },
  { id: 14, kbId: 5, kbName: "合规与法务库", title: "数据出境合规指引", fileName: "数据出境合规指引.pdf", fileExt: "pdf", mimeType: "application/pdf", sourceType: "UPLOAD", fileSize: 1_800_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PENDING_REVIEW", sensitivity: "RESTRICTED", ownerName: "孙志强", chunkCount: 40, updatedAt: "2026-08-07T02:40:00Z" },
  { id: 15, kbId: 5, kbName: "合规与法务库", title: "员工保密协议模板", fileName: "员工保密协议模板.docx", fileExt: "docx", mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", sourceType: "UPLOAD", fileSize: 320_000, versionNo: 1, ingestStatus: "READY", reviewStatus: "PUBLISHED", sensitivity: "RESTRICTED", ownerName: "孙志强", chunkCount: 18, updatedAt: "2026-08-06T09:00:00Z" },
  { id: 16, kbId: 2, kbName: "后端技术规范库", title: "Confluence 文档自动同步", fileName: "confluence-space-backend", fileExt: "html", mimeType: "text/html", sourceType: "CONNECTOR", fileSize: 5_600_000, versionNo: 1, ingestStatus: "INDEXING", reviewStatus: "PENDING_REVIEW", sensitivity: "INTERNAL", ownerName: "李佳宁", chunkCount: 0, updatedAt: "2026-08-10T01:00:00Z" },
];

export const documentDetails: Record<number, DocumentDetail> = Object.fromEntries(
  documents.map((doc) => [
    doc.id,
    {
      ...doc,
      versions: documentVersions[doc.id] ?? [
        {
          versionNo: 1,
          fileSize: doc.fileSize,
          ingestStatus: doc.ingestStatus,
          safetyStatus: "PASSED",
          chunkCount: doc.chunkCount,
          createdBy: doc.ownerName,
          createdAt: doc.updatedAt,
        },
      ],
      tags: [],
      isFavorite: false,
    },
  ]),
);

export function getDocumentDetail(id: number): DocumentDetail | undefined {
  return documentDetails[id];
}
