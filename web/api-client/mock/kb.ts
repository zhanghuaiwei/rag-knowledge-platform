import type { KbApi } from "@/api-client/contracts/kb";
import type {
  AddKbMemberInput,
  CreateKbInput,
  Kb,
  KbMember,
  KbMemberRole,
  PageParams,
  UpdateKbInput,
} from "@/api-client/types";
import { db, delay, paginate } from "@/mocks/db";
import { appendAudit, nextId, now } from "@/mocks/helpers";

function notFound(resource: string): never {
  throw new Error(`${resource}不存在`);
}

/** 同步当前用户角色与成员关系：创建/克隆后调用，保证 kbs 与 kbMembers 一致。 */
function syncKbMembers(kb: Kb): void {
  db.kbMembers[kb.id] = kb.members;
}

export const kbApi: KbApi = {
  async listKbs(params: PageParams = {}) {
    await delay();
    return paginate(db.kbs, params.page, params.size);
  },

  async getKb(id: number) {
    await delay(120);
    const kb = db.kbs.find((item) => item.id === id);
    if (!kb) notFound("知识库");
    return kb;
  },

  async updateKb(id, input: UpdateKbInput) {
    await delay(300);
    const kb = db.kbs.find((item) => item.id === id);
    if (!kb) notFound("知识库");
    if (input.name !== undefined) kb.name = input.name;
    if (input.description !== undefined) kb.description = input.description;
    if (input.requiresReview !== undefined) kb.requiresReview = input.requiresReview;
    kb.updatedAt = now();
    appendAudit({ action: "kb.update", resourceType: "KB", resourceId: id });
    return kb;
  },

  async createKb(input: CreateKbInput) {
    await delay(400);
    if (db.kbs.some((item) => item.name === input.name.trim())) {
      throw new Error("知识库名称在租户内已存在");
    }
    const id = nextId(db.kbs);
    const me = db.currentUser;
    const kb: Kb = {
      id,
      name: input.name.trim(),
      description: input.description?.trim() ?? "",
      visibility: input.visibility,
      status: "ACTIVE",
      role: "OWNER",
      documentCount: 0,
      chunkCount: 0,
      dataRegion: input.dataRegion ?? "default",
      indexProfileName: "standard-1024",
      requiresReview: input.requiresReview ?? true,
      ocrEnabled: input.ocrEnabled ?? false,
      createdAt: now(),
      updatedAt: now(),
      members: [{ userId: me.id, userName: me.name, role: "OWNER" }],
    };
    db.kbs.unshift(kb);
    syncKbMembers(kb);
    appendAudit({ action: "kb.create", resourceType: "KB", resourceId: id });
    return kb;
  },

  async cloneKb(id: number) {
    await delay(400);
    const source = db.kbs.find((item) => item.id === id);
    if (!source) notFound("知识库");
    const newId = nextId(db.kbs);
    const clone: Kb = {
      ...source,
      id: newId,
      name: `${source.name}（副本）`,
      status: "CLONING",
      role: "OWNER",
      members: [{ userId: db.currentUser.id, userName: db.currentUser.name, role: "OWNER" }],
      createdAt: now(),
      updatedAt: now(),
    };
    db.kbs.unshift(clone);
    syncKbMembers(clone);
    appendAudit({ action: "kb.clone", resourceType: "KB", resourceId: newId });
    return clone;
  },

  async archiveKb(id: number) {
    await delay(300);
    const kb = db.kbs.find((item) => item.id === id);
    if (!kb) notFound("知识库");
    kb.status = "ARCHIVED";
    kb.updatedAt = now();
    appendAudit({ action: "kb.archive", resourceType: "KB", resourceId: id });
    return kb;
  },

  async deleteKb(id: number) {
    await delay(300);
    const index = db.kbs.findIndex((item) => item.id === id);
    if (index < 0) notFound("知识库");
    db.kbs.splice(index, 1);
    delete db.kbMembers[id];
    appendAudit({ action: "kb.delete", resourceType: "KB", resourceId: id });
  },

  async listKbMembers(kbId: number) {
    await delay(120);
    const members = db.kbMembers[kbId];
    if (!members) notFound("知识库");
    return members;
  },

  async addKbMember(kbId: number, input: AddKbMemberInput) {
    await delay(200);
    const members = db.kbMembers[kbId];
    if (!members) notFound("知识库");
    const user = db.users.find((item) => item.id === input.userId);
    if (!user) throw new Error("用户不存在");
    if (members.some((m) => m.userId === input.userId)) throw new Error("该用户已是知识库成员");
    members.push({ userId: user.id, userName: user.name, role: input.role });
    appendAudit({ action: "kb.member.add", resourceType: "KB", resourceId: kbId });
    return members;
  },

  async updateKbMemberRole(kbId: number, userId: number, role: KbMemberRole) {
    await delay(200);
    const members = db.kbMembers[kbId];
    if (!members) notFound("知识库");
    const member = members.find((m) => m.userId === userId);
    if (!member) throw new Error("成员不存在");
    member.role = role;
    appendAudit({ action: "kb.member.role", resourceType: "KB", resourceId: kbId });
    return members;
  },

  async removeKbMember(kbId: number, userId: number) {
    await delay(200);
    const members = db.kbMembers[kbId];
    if (!members) notFound("知识库");
    const member = members.find((m) => m.userId === userId);
    if (!member) throw new Error("成员不存在");
    if (member.role === "OWNER") throw new Error("不能移除唯一所有者");
    const index = members.findIndex((m) => m.userId === userId);
    members.splice(index, 1);
    appendAudit({ action: "kb.member.remove", resourceType: "KB", resourceId: kbId });
    return members;
  },
};
