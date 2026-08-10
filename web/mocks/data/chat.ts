/**
 * mock 问答会话与消息数据（对齐 chat_session / chat_message / chat_message_source 语义）。
 * ASSISTANT 消息携带 sources（引用）与 suggestions（追问建议）。
 */
import type { ChatMessage, ChatSession, ChatSource } from "@/api-client/types";

export const chatSessions: ChatSession[] = [
  { id: 101, title: "分布式事务模式选型", status: "ACTIVE", kbIds: [1, 2], messageCount: 4, createdAt: "2026-08-10T01:00:00Z", updatedAt: "2026-08-10T01:05:00Z" },
  { id: 102, title: "OpenTelemetry 接入咨询", status: "ACTIVE", kbIds: [1], messageCount: 2, createdAt: "2026-08-09T08:00:00Z", updatedAt: "2026-08-09T08:10:00Z" },
  { id: 103, title: "数据库分页优化", status: "ARCHIVED", kbIds: [2], messageCount: 3, createdAt: "2026-08-05T03:00:00Z", updatedAt: "2026-08-05T03:20:00Z" },
];

const sourceSample: ChatSource = {
  documentId: 5,
  fileName: "分布式事务模式选型.pdf",
  pageNo: 3,
  sectionTitle: "Seata AT 与 TCC",
  chunkId: "9f3b…7a",
  score: 0.91,
};

export const chatMessagesBySession: Record<number, ChatMessage[]> = {
  101: [
    {
      id: 501, sessionId: 101, seq: 1, role: "USER",
      content: "分布式事务有哪些常用模式？Seata AT 与 TCC 如何取舍？",
      answerStatus: null, confidence: null, feedback: 0,
      tokenIn: 0, tokenOut: 0, modelName: "",
      sources: [], suggestions: [], createdAt: "2026-08-10T01:00:00Z",
    },
    {
      id: 502, sessionId: 101, seq: 2, role: "ASSISTANT",
      content:
        "常用分布式事务模式包括两阶段提交（2PC/XA）、TCC、SAGA 与基于消息（本地消息表 / 事务 outbox）的模式。\n\n**Seata AT 模式**对业务无侵入，适合关系库内的一致性场景，但无法覆盖对象存储、搜索索引与模型调用等外部副作用。**TCC** 由业务显式提供 Try/Confirm/Cancel，控制力强但编码成本高。\n\n本项目（通用企业知识库平台）选型结论是：关系库内使用**本地事务**，跨外部副作用使用 **transactional outbox + 幂等消费者 + 补偿任务**，避免分布式事务引入的复杂性与可用性损失。",
      answerStatus: "ANSWERED", confidence: 0.87, feedback: 1,
      tokenIn: 320, tokenOut: 180, modelName: "claude-sonnet-5",
      sources: [sourceSample, { ...sourceSample, pageNo: 5, sectionTitle: "outbox 与幂等", chunkId: "2c41…0f", score: 0.78 }],
      suggestions: ["outbox 与本地消息表的区别？", "TCC 实现成本如何评估？", "SAGA 适合哪些场景？"],
      createdAt: "2026-08-10T01:00:20Z",
    },
    {
      id: 503, sessionId: 101, seq: 3, role: "USER",
      content: "outbox 模式如何保证消息不丢、不重复？",
      answerStatus: null, confidence: null, feedback: 0,
      tokenIn: 0, tokenOut: 0, modelName: "",
      sources: [], suggestions: [], createdAt: "2026-08-10T01:02:00Z",
    },
    {
      id: 504, sessionId: 101, seq: 4, role: "ASSISTANT",
      content:
        "outbox 模式通过“领域变更与 outbox 事件同事务提交”保证不丢：业务写操作与事件写入在同一数据库事务内完成，二者要么都成功、要么都回滚。\n\n**不重复**由幂等消费者保证：消费者按 eventId / idempotencyKey 去重；失败指数退避，达到上限进入 dead letter 供人工处置。事件 transport 起步可由 PostgreSQL SKIP LOCKED worker 实现。",
      answerStatus: "ANSWERED", confidence: 0.82, feedback: 0,
      tokenIn: 280, tokenOut: 150, modelName: "claude-sonnet-5",
      sources: [{ ...sourceSample, pageNo: 6, sectionTitle: "事件与一致性", chunkId: "8e12…ab", score: 0.85 }],
      suggestions: ["outbox 的清理与归档策略？", "多消费者如何保证顺序？"],
      createdAt: "2026-08-10T01:02:30Z",
    },
  ],
  102: [
    {
      id: 601, sessionId: 102, seq: 1, role: "USER",
      content: "OpenTelemetry 如何接入 Java 服务？",
      answerStatus: null, confidence: null, feedback: 0,
      tokenIn: 0, tokenOut: 0, modelName: "",
      sources: [], suggestions: [], createdAt: "2026-08-09T08:00:00Z",
    },
    {
      id: 602, sessionId: 102, seq: 2, role: "ASSISTANT",
      content:
        "接入 OpenTelemetry 推荐使用自动埋点（Java agent）先行，再补充业务 span 与自定义属性。注意：敏感的 tenant/user 信息不得无控制进入第三方 observability backend，统一使用 W3C Trace Context 作为传播协议。",
      answerStatus: "ANSWERED", confidence: 0.9, feedback: 1,
      tokenIn: 260, tokenOut: 130, modelName: "claude-sonnet-5",
      sources: [{ documentId: 3, fileName: "OpenTelemetry接入指南.pdf", pageNo: 2, sectionTitle: "Java 接入", chunkId: "1aa2…cd", score: 0.88 }],
      suggestions: ["Span 采样策略如何设置？", "日志如何与 trace 关联？"],
      createdAt: "2026-08-09T08:10:00Z",
    },
  ],
  103: [
    {
      id: 701, sessionId: 103, seq: 1, role: "USER",
      content: "深分页性能问题如何解决？",
      answerStatus: null, confidence: null, feedback: 0,
      tokenIn: 0, tokenOut: 0, modelName: "",
      sources: [], suggestions: [], createdAt: "2026-08-05T03:00:00Z",
    },
    {
      id: 702, sessionId: 103, seq: 2, role: "ASSISTANT",
      content:
        "深分页建议优先使用**游标分页（cursor-based）**替代 OFFSET：游标基于索引直接定位，页码大时性能稳定。若必须使用页码，可结合 Keyset 分页或限制最大翻页深度。",
      answerStatus: "ANSWERED", confidence: 0.75, feedback: -1,
      tokenIn: 200, tokenOut: 110, modelName: "claude-sonnet-5",
      sources: [{ documentId: 8, fileName: "SQL与数据库规范.md", pageNo: 4, sectionTitle: "分页规范", chunkId: "3b3f…19", score: 0.72 }],
      suggestions: [], createdAt: "2026-08-05T03:15:00Z",
    },
  ],
};
