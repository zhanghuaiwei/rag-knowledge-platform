import type { MiscApi } from "@/api-client/contracts/misc";
import type { FavoriteListParams } from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";

function notFound(resource: string): never {
  throw new Error(`${resource}不存在`);
}

export const miscApi: MiscApi = {
  async listFavorites(params: FavoriteListParams = {}) {
    await delay();
    const items = params.documentId ? db.favorites.filter((f) => f.documentId === params.documentId) : db.favorites;
    return paginate(items, params.page, params.size);
  },
  async listConnectors() {
    await delay();
    return db.connectors;
  },
  async listTasks() {
    await delay();
    return db.tasks;
  },
  async cancelTask(id: number) {
    await delay(250);
    const task = db.tasks.find((item) => item.id === id);
    if (!task) notFound("任务");
    if (task.status === "RUNNING" || task.status === "PENDING") {
      task.status = "CANCELLED";
      task.progress = 100;
      task.finishedAt = new Date().toISOString();
    }
  },
  async listNotifications() {
    await delay();
    return db.notifications;
  },
  async markNotificationRead(id: number) {
    await delay(80);
    const item = db.notifications.find((n) => n.id === id);
    if (item) item.read = true;
  },
  async markAllNotificationsRead() {
    await delay(120);
    db.notifications.forEach((n) => {
      n.read = true;
    });
  },
};
