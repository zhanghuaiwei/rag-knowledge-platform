/**
 * mock 租户元数据 schema（GKB-04）。
 * 真实实现由租户 schema 驱动表单，此处为演示数据，字段类型与受控词表对齐契约。
 */
import type { MetadataSchema } from "@/api-client/types";

export const metadataSchemas: MetadataSchema[] = [
  {
    id: 1,
    name: "标准文档元数据",
    description: "全租户默认的文档元数据 schema，必填：所有者、业务域、敏感级。",
    status: "PUBLISHED",
    fields: [
      { key: "owner", label: "内容所有者", type: "STRING", required: true },
      { key: "domain", label: "业务域", type: "ENUM", required: true, options: ["产品研发", "市场营销", "人力资源", "财务合规", "客户服务"] },
      { key: "sensitivity", label: "敏感级", type: "ENUM", required: true, options: ["公开", "内部", "机密", "绝密"] },
      { key: "tags", label: "标签", type: "MULTI_VALUE", required: false },
      { key: "reviewDate", label: "复审日期", type: "DATE", required: false },
    ],
    updatedAt: "2026-08-08T09:00:00Z",
  },
  {
    id: 2,
    name: "合规类文档元数据",
    description: "法务合规库专用，额外要求：适用法域、保全年限。",
    status: "DRAFT",
    fields: [
      { key: "jurisdiction", label: "适用法域", type: "ENUM", required: true, options: ["中国", "欧盟", "美国"] },
      { key: "holdYears", label: "保全年限", type: "STRING", required: false },
    ],
    updatedAt: "2026-08-10T02:00:00Z",
  },
];
