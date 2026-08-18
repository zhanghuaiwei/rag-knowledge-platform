/** 杂项类型：收藏 / 连接器。 */
import type { PageParams } from "@/api-client/types/common";

export interface FavoriteItem {
  documentId: number;
  title: string;
  fileName: string;
  kbName: string;
  savedAt: string;
}

export interface Connector {
  id: number;
  name: string;
  providerKey: string;
  syncMode: "MANUAL" | "SCHEDULED" | "WEBHOOK";
  status: "ACTIVE" | "PAUSED" | "ERROR";
  lastSuccessAt: string | null;
  lastErrorCode: string | null;
  cursorAgeMin: number;
  counts: {
    discovered: number;
    created: number;
    updated: number;
    deleted: number;
    failed: number;
  };
}

export interface FavoriteListParams extends PageParams {
  documentId?: number;
}

/** 连接器同步类型（对齐后端 ck_sync_job_type 白名单）。 */
export type ConnectorSyncType = "FULL" | "INCREMENTAL" | "WEBHOOK" | "RECONCILE";

/** 触发连接器同步入参（POST /connections/{id}/sync）。 */
export interface SyncConnectorInput {
  syncType: ConnectorSyncType;
}

/** 同步任务详情（GET /sync-jobs/{jobId}；状态值：QUEUED/RUNNING/SUCCEEDED/PARTIAL/FAILED/CANCELLED）。 */
export interface SyncJob {
  id: number;
  /** 所属连接器 id。 */
  connectionId: number;
  /** 同步类型（FULL/INCREMENTAL/WEBHOOK/RECONCILE）。 */
  syncType: string;
  /** 任务状态（QUEUED/RUNNING/SUCCEEDED/PARTIAL/FAILED/CANCELLED）。 */
  status: string;
  /** 本轮发现的对象总数。 */
  discovered: number;
  /** 失败对象清单（最多 20 条，超出截断）。 */
  failedObjects: string[];
  /** 最近一次成功时间（ISO 字符串，可为 null）。 */
  lastSuccessAt: string | null;
  /** 失败时的错误码（可为 null）。 */
  errorCode: string | null;
  /** 任务创建时间（ISO 字符串）。 */
  createdAt: string;
}
