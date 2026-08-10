/**
 * mock 内存数据仓库：聚合各数据集，并提供分页 / 延迟等查询工具。
 *
 * 仅供本地开发与演示；真实数据以后端 PostgreSQL 为准（docs/deploy/ddl）。
 * 不要把 mock 数据描述为真实验收证据（AGENTS.md 开发红线）。
 */
import type { PageResult } from "@/api-client/types";
import { auditLogs, apiKeys, reviewItems, webhooks } from "@/mocks/data/admin";
import {
  dailyUsage,
  dau,
  knowledgeHealth,
  tokenCosts,
  topDocuments,
} from "@/mocks/data/analytics";
import {
  chatMessagesBySession,
  chatSessions,
} from "@/mocks/data/chat";
import {
  documents,
  getDocumentDetail,
} from "@/mocks/data/documents";
import { kbs, kbMembers } from "@/mocks/data/kbs";
import { connectors, favorites, tags } from "@/mocks/data/misc";
import { searchItems } from "@/mocks/data/search";
import { currentUser, orgs, users } from "@/mocks/data/users";

export function delay(ms = 180): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export function paginate<T>(items: T[], page = 1, size = 20): PageResult<T> {
  const safeSize = size < 1 ? 20 : size;
  const start = (page - 1) * safeSize;
  const pageItems = items.slice(start, start + safeSize);
  return {
    items: pageItems,
    total: items.length,
    page,
    size: safeSize,
    hasMore: start + safeSize < items.length,
  };
}

export const db = {
  currentUser,
  users,
  orgs,
  kbs,
  kbMembers,
  documents,
  getDocumentDetail,
  chatSessions,
  chatMessagesBySession,
  searchItems,
  dailyUsage,
  tokenCosts,
  topDocuments,
  dau,
  knowledgeHealth,
  auditLogs,
  apiKeys,
  webhooks,
  reviewItems,
  tags,
  favorites,
  connectors,
};
