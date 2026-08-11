/** 异步任务与通知类型。 */

/** 异步任务类型。 */
export type TaskType = "UPLOAD" | "INGEST" | "INDEX_BUILD" | "SYNC" | "DELETE" | "EXPORT";
export type TaskStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLING" | "CANCELLED";

export interface Task {
  id: number;
  type: TaskType;
  status: TaskStatus;
  title: string;
  /** 0-100,运行中/完成态展示进度。 */
  progress: number;
  /** 关联资源(文档/知识库/连接器),可选。 */
  resourceType?: string;
  resourceId?: string;
  startedAt: string;
  finishedAt: string | null;
  message?: string;
}

/** 通知类型。 */
export type NotificationKind = "TASK_DONE" | "TASK_FAILED" | "REVIEW_TODO" | "QUOTA_WARN" | "SYSTEM";
export type NotificationLevel = "info" | "success" | "warning" | "error";

export interface NotificationItem {
  id: number;
  kind: NotificationKind;
  level: NotificationLevel;
  title: string;
  body: string;
  read: boolean;
  createdAt: string;
  /** 可选跳转路径。 */
  href?: string;
}
