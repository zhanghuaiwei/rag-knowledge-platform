/**
 * 知识库域真实 HTTP transport（对齐 OpenAPI knowledge-bases）。
 * 克隆为异步任务（202 + Task）：轮询终态后按 resourceId 获取新库。
 */
import type { KbApi } from "@/api-client/contracts/kb";
import type {
  AddKbMemberInput,
  CreateKbInput,
  Kb,
  KbMember,
  KbMemberRole,
  PageParams,
  PageResult,
  Task,
  UpdateKbInput,
} from "@/api-client/types";
import { request, requestVoid, waitForTask } from "@/api-client/http/client";
import { ApiError } from "@/api-client/http/errors";

export const kbApi: KbApi = {
  async listKbs(params: PageParams = {}) {
    return request<PageResult<Kb>>({
      method: "GET",
      url: "/kbs",
      params: { page: params.page ?? 1, size: params.size ?? 20 },
    });
  },

  async getKb(id: number) {
    return request<Kb>({ method: "GET", url: `/kbs/${id}` });
  },

  async updateKb(id: number, input: UpdateKbInput) {
    return request<Kb>({
      method: "PATCH",
      url: `/kbs/${id}`,
      data: {
        name: input.name,
        description: input.description,
        requiresReview: input.requiresReview,
      },
    });
  },

  async createKb(input: CreateKbInput) {
    return request<Kb>({ method: "POST", url: "/kbs", data: input });
  },

  async cloneKb(id: number) {
    const task = await request<Task>({ method: "POST", url: `/kbs/${id}/clone`, data: {} });
    const finished = await waitForTask(task.id);
    const newId = Number(finished.resourceId);
    if (!Number.isFinite(newId) || newId <= 0) {
      throw new ApiError("克隆任务未返回新知识库标识", { code: "E-TASK" });
    }
    return request<Kb>({ method: "GET", url: `/kbs/${newId}` });
  },

  async archiveKb(id: number) {
    return request<Kb>({ method: "POST", url: `/kbs/${id}/archive` });
  },

  async deleteKb(id: number) {
    await requestVoid({ method: "DELETE", url: `/kbs/${id}` });
  },

  async listKbMembers(kbId: number) {
    return request<KbMember[]>({ method: "GET", url: `/kbs/${kbId}/members` });
  },

  async addKbMember(kbId: number, input: AddKbMemberInput) {
    await request<KbMember>({ method: "POST", url: `/kbs/${kbId}/members`, data: input });
    // 契约返回完整成员数组：写入后回读
    return request<KbMember[]>({ method: "GET", url: `/kbs/${kbId}/members` });
  },

  async updateKbMemberRole(kbId: number, userId: number, role: KbMemberRole) {
    await request<KbMember>({
      method: "POST",
      url: `/kbs/${kbId}/members`,
      data: { userId, role },
    });
    return request<KbMember[]>({ method: "GET", url: `/kbs/${kbId}/members` });
  },

  async removeKbMember(kbId: number, userId: number) {
    await requestVoid({ method: "DELETE", url: `/kbs/${kbId}/members/${userId}` });
    return request<KbMember[]>({ method: "GET", url: `/kbs/${kbId}/members` });
  },
};
