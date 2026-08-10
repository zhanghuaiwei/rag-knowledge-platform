/** 展示格式化工具：时间、文件大小、数字、状态文案映射。 */

export function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function formatDate(iso: string | null): string {
  return formatDateTime(iso).slice(0, 10);
}

export function formatRelative(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "刚刚";
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 30) return `${days} 天前`;
  return formatDate(iso);
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

export function formatNumber(n: number): string {
  return n.toLocaleString("zh-CN");
}

export function formatPercent(ratio: number): string {
  return `${(ratio * 100).toFixed(1)}%`;
}

export function formatCost(cost: number): string {
  return `¥${cost.toFixed(cost < 1 ? 4 : 2)}`;
}

/**
 * 枚举 → 中文文案与 antd Tag 预设色（展示层语义，枚举值本身以契约为准）。
 * 颜色值直接对应 antd <Tag color>：success / error / warning / processing / blue / purple / 默认。
 */
export const STATUS_TEXT = {
  ingest: {
    UPLOADING: ["上传中", "processing"], QUARANTINED: ["隔离区", "warning"], SCANNING: ["安全扫描", "processing"],
    PARSING: ["解析中", "processing"], CHUNKING: ["分块中", "processing"], EMBEDDING: ["向量化", "processing"],
    INDEXING: ["索引中", "processing"], READY: ["就绪", "success"], FAILED: ["失败", "error"], BLOCKED: ["安全阻断", "error"],
  },
  review: {
    DRAFT: ["草稿", "default"], PENDING_REVIEW: ["待审核", "warning"], PUBLISHED: ["已发布", "success"],
    REJECTED: ["已驳回", "error"], WITHDRAWN: ["已撤回", "default"],
  },
  sensitivity: {
    PUBLIC: ["公开", "success"], INTERNAL: ["内部", "processing"], CONFIDENTIAL: ["机密", "warning"], RESTRICTED: ["绝密", "error"],
  },
  answer: {
    ANSWERED: ["已回答", "success"], NO_ANSWER: ["未找到答案", "warning"],
    LOW_CONFIDENCE: ["低置信度", "warning"], BLOCKED: ["已拦截", "error"],
  },
  kbRole: { OWNER: ["所有者", "purple"], EDITOR: ["编辑", "blue"], VIEWER: ["查看者", "default"] },
} as const;

export type StatusDomain = keyof typeof STATUS_TEXT;

export function statusText(domain: StatusDomain, value: string): [string, string] {
  const map = STATUS_TEXT[domain] as Record<string, readonly [string, string]>;
  return [...(map[value] ?? [value, ""])] as [string, string];
}
