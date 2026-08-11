import type {
  AddKbMemberInput,
  CreateKbInput,
  Kb,
  KbMember,
  KbMemberRole,
  PageParams,
  PageResult,
  UpdateKbInput,
} from "@/api-client/types";

/** 知识库域契约（F2.1）。 */
export interface KbApi {
  listKbs(params?: PageParams): Promise<PageResult<Kb>>;
  getKb(id: number): Promise<Kb>;
  updateKb(id: number, input: UpdateKbInput): Promise<Kb>;
  createKb(input: CreateKbInput): Promise<Kb>;
  cloneKb(id: number): Promise<Kb>;
  archiveKb(id: number): Promise<Kb>;
  deleteKb(id: number): Promise<void>;
  listKbMembers(kbId: number): Promise<KbMember[]>;
  addKbMember(kbId: number, input: AddKbMemberInput): Promise<KbMember[]>;
  updateKbMemberRole(kbId: number, userId: number, role: KbMemberRole): Promise<KbMember[]>;
  removeKbMember(kbId: number, userId: number): Promise<KbMember[]>;
}
