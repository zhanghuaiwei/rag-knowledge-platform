/**
 * mock 数据层统一入口。
 *
 * 数据与 API 契约类型对齐（api-client/types.ts）；前端页面只经
 * api-client 消费数据，mock 与真实 http 的切换由 api-client 内部完成。
 */
export { db, delay, paginate } from "@/mocks/db";
