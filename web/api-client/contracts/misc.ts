import type {
  Connector,
  FavoriteItem,
  FavoriteListParams,
  NotificationItem,
  PageResult,
  Task,
} from "@/api-client/types";

/** 杂项与任务/通知契约。 */
export interface MiscApi {
  listFavorites(params?: FavoriteListParams): Promise<PageResult<FavoriteItem>>;
  listConnectors(): Promise<Connector[]>;
  listTasks(): Promise<Task[]>;
  cancelTask(id: number): Promise<void>;
  listNotifications(): Promise<NotificationItem[]>;
  markNotificationRead(id: number): Promise<void>;
  markAllNotificationsRead(): Promise<void>;
}
