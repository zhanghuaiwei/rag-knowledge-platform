import type {
  Connector,
  FavoriteItem,
  FavoriteListParams,
  NotificationItem,
  PageResult,
  SyncConnectorInput,
  SyncJob,
  Task,
} from "@/api-client/types";

/** 杂项与任务/通知契约。 */
export interface MiscApi {
  listFavorites(params?: FavoriteListParams): Promise<PageResult<FavoriteItem>>;
  listConnectors(): Promise<Connector[]>;
  /** 触发连接器手动同步：202 + Task（resourceType=SYNC_JOB，resourceId=同步任务 id）。 */
  syncConnector(connectionId: number, input: SyncConnectorInput): Promise<Task>;
  /** 查询单个同步任务详情（轮询执行状态用）。 */
  getSyncJob(jobId: number): Promise<SyncJob>;
  listTasks(): Promise<Task[]>;
  cancelTask(id: number): Promise<void>;
  listNotifications(): Promise<NotificationItem[]>;
  markNotificationRead(id: number): Promise<void>;
  markAllNotificationsRead(): Promise<void>;
}
