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
