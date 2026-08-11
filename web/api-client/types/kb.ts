/** 知识库域类型。 */

export type KbVisibility = "PRIVATE" | "TENANT";
export type KbStatus = "ACTIVE" | "ARCHIVED" | "DELETING" | "CLONING";
export type KbMemberRole = "OWNER" | "EDITOR" | "VIEWER";

export interface KbMember {
  userId: number;
  userName: string;
  role: KbMemberRole;
}

export interface Kb {
  id: number;
  name: string;
  description: string;
  visibility: KbVisibility;
  status: KbStatus;
  role: KbMemberRole; // 当前用户在该 KB 的角色
  documentCount: number;
  chunkCount: number;
  dataRegion: string;
  indexProfileName: string;
  requiresReview: boolean;
  ocrEnabled: boolean;
  createdAt: string;
  updatedAt: string;
  members: KbMember[];
}

/** 知识库可编辑字段（索引 Profile / 数据区域等不可变字段不在其列）。 */
export interface UpdateKbInput {
  name?: string;
  description?: string;
  requiresReview?: boolean;
}

/** 创建知识库入参（对齐新建向导字段）。 */
export interface CreateKbInput {
  name: string;
  description?: string;
  visibility: KbVisibility;
  domain?: string;
  sensitivity?: string;
  retention?: string;
  dataRegion?: string;
  modelPolicy?: string;
  requiresReview?: boolean;
  ocrEnabled?: boolean;
}

export interface AddKbMemberInput {
  userId: number;
  role: KbMemberRole;
}
