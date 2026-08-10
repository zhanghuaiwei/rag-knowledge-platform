/**
 * mock 全文搜索命中数据（对齐 07-API契约 §2.7 响应结构）。
 */
import type { SearchItem } from "@/api-client/types";

export const searchItems: SearchItem[] = [
  {
    documentId: 5,
    fileName: "分布式事务模式选型.pdf",
    kbId: 1,
    pageNo: 3,
    sectionTitle: "Seata AT 与 TCC",
    fileExt: "pdf",
    snippet: "…<mark>分布式事务</mark>常见模式包括 2PC、TCC、SAGA 与基于消息的 outbox 模式…",
    score: 18.2,
    updatedAt: "2026-08-01T02:30:00Z",
  },
  {
    documentId: 1,
    fileName: "微服务架构设计规范.pdf",
    kbId: 1,
    pageNo: 7,
    sectionTitle: "服务间通信",
    fileExt: "pdf",
    snippet: "…服务间调用采用同步 REST + 异步事件，<mark>分布式事务</mark>边界收敛在聚合内…",
    score: 15.7,
    updatedAt: "2026-08-08T06:10:00Z",
  },
  {
    documentId: 8,
    fileName: "SQL与数据库规范.md",
    kbId: 2,
    pageNo: 4,
    sectionTitle: "分页规范",
    fileExt: "md",
    snippet: "…深分页使用<mark>游标分页</mark>替代 OFFSET，避免全表扫描…",
    score: 12.1,
    updatedAt: "2026-08-05T03:20:00Z",
  },
  {
    documentId: 3,
    fileName: "OpenTelemetry接入指南.pdf",
    kbId: 1,
    pageNo: 2,
    sectionTitle: "Java 接入",
    fileExt: "pdf",
    snippet: "…接入 <mark>OpenTelemetry</mark> 推荐 Java agent 自动埋点先行…",
    score: 9.8,
    updatedAt: "2026-08-10T00:30:00Z",
  },
  {
    documentId: 13,
    fileName: "产品白皮书v3.pdf",
    kbId: 4,
    pageNo: 12,
    sectionTitle: "架构特性",
    fileExt: "pdf",
    snippet: "…平台提供<mark>知识接入、治理、检索</mark>与引用问答的完整能力…",
    score: 7.4,
    updatedAt: "2026-07-25T06:00:00Z",
  },
];
