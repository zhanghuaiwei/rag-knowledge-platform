import type { SearchApi } from "@/api-client/contracts/search";
import type { SearchParams } from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";

export const searchApi: SearchApi = {
  async search(params: SearchParams) {
    await delay(200);
    const keyword = params.keyword.trim().toLowerCase();
    const kbFilter = params.kbIds?.length ? new Set(params.kbIds) : null;
    const from = params.dateFrom ? new Date(params.dateFrom).getTime() : null;
    const to = params.dateTo ? new Date(params.dateTo).getTime() : null;

    let items = db.searchItems.filter((item) => {
      if (!item.fileName.toLowerCase().includes(keyword)) return false;
      if (kbFilter && !kbFilter.has(item.kbId)) return false;
      if (params.fileType && item.fileExt !== params.fileType) return false;
      const time = new Date(item.updatedAt).getTime();
      if (from !== null && time < from) return false;
      if (to !== null && time > to) return false;
      return true;
    });

    // 排序：按时间降序，或按相关度（mock：以命中关键词次数近似相关度）
    if (params.sort === "TIME") {
      items = [...items].sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
    } else {
      const relevance = (name: string) => name.toLowerCase().split(keyword).length - 1;
      items = [...items].sort((a, b) => relevance(b.fileName) - relevance(a.fileName) || b.score - a.score);
    }

    return paginate(items, params.page, params.size);
  },
};
