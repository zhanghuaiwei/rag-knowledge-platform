/**
 * API 契约类型统一出口（按域拆分，兼容旧 "@/api-client/types" 引用）。
 * 枚举值以服务端契约为准，前端不重复定义"魔法值"。
 */
export * from "@/api-client/types/common";
export * from "@/api-client/types/kb";
export * from "@/api-client/types/document";
export * from "@/api-client/types/chat";
export * from "@/api-client/types/search";
export * from "@/api-client/types/analytics";
export * from "@/api-client/types/admin";
export * from "@/api-client/types/governance";
export * from "@/api-client/types/task";
export * from "@/api-client/types/misc";
