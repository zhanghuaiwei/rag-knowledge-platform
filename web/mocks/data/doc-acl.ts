/**
 * mock 文档级 ACL（F2.14 / GKB-04）。
 * 白名单语义：文档有记录时按记录判定，无记录时继承知识库成员权限。
 * 权限点三档：view_excerpt / view_content / download_original。
 */
import type { AclEntry } from "@/api-client/types";

/** 文档 id → ACL 条目列表（无记录即继承 KB 权限）。 */
export const documentAcl: Record<number, AclEntry[]> = {
  13: [
    { id: 1, principalType: "ORG", principalName: "研发中心", permissions: ["VIEW_EXCERPT", "VIEW_CONTENT"] },
    { id: 2, principalType: "ROLE", principalName: "KNOWLEDGE_ADMIN", permissions: ["VIEW_EXCERPT", "VIEW_CONTENT", "DOWNLOAD_ORIGINAL"] },
  ],
  14: [
    { id: 1, principalType: "USER", principalName: "孙志强", permissions: ["VIEW_EXCERPT", "VIEW_CONTENT", "DOWNLOAD_ORIGINAL"] },
    { id: 2, principalType: "USER", principalName: "张怀伟", permissions: ["VIEW_EXCERPT", "VIEW_CONTENT"] },
  ],
};
