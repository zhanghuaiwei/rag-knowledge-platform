/**
 * 杂项域真实 HTTP transport：收藏 / 连接器 / 异步任务 / 通知。
 */
import type { MiscApi } from "@/api-client/contracts/misc";
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
import { request, requestVoid } from "@/api-client/http/client";

export const miscApi: MiscApi = {
  async listFavorites(params: FavoriteListParams = {}) {
    return request<PageResult<FavoriteItem>>({
      method: "GET",
      url: "/favorites",
      params: { page: params.page ?? 1, size: params.size ?? 20 },
    });
  },

  async listConnectors() {
    return request<Connector[]>({ method: "GET", url: "/connections" });
  },

  async syncConnector(connectionId: number, input: SyncConnectorInput) {
    // 202 + Task：resourceType="SYNC_JOB"、resourceId=同步任务 id，供 getSyncJob 轮询执行状态
    return request<Task>({
      method: "POST",
      url: `/connections/${connectionId}/sync`,
      data: { syncType: input.syncType },
    });
  },

  async getSyncJob(jobId: number) {
    return request<SyncJob>({ method: "GET", url: `/sync-jobs/${jobId}` });
  },

  async listTasks() {
    return request<Task[]>({ method: "GET", url: "/tasks" });
  },

  async cancelTask(id: number) {
    await requestVoid({ method: "POST", url: `/tasks/${id}` });
  },

  async listNotifications() {
    return request<NotificationItem[]>({ method: "GET", url: "/notifications" });
  },

  async markNotificationRead(id: number) {
    await requestVoid({ method: "POST", url: `/notifications/${id}/read` });
  },

  async markAllNotificationsRead() {
    await requestVoid({ method: "POST", url: "/notifications/read-all" });
  },
};
