import { describe, expect, it } from "vitest";

import { mockClient } from "@/api-client/mock";

describe("mock client 数据完整性", () => {
  it("知识库列表分页且每个 KB 有成员与当前角色", async () => {
    const page = await mockClient.listKbs({ page: 1, size: 2 });
    expect(page.items).toHaveLength(2);
    expect(page.total).toBeGreaterThan(2);
    expect(page.hasMore).toBe(true);

    const kb = await mockClient.getKb(page.items[0].id);
    expect(kb.members.length).toBeGreaterThan(0);
    expect(kb.role).toBeTruthy();
  });

  it("文档列表支持状态过滤", async () => {
    const page = await mockClient.listDocuments({ ingestStatus: "READY" });
    expect(page.items.length).toBeGreaterThan(0);
    expect(page.items.every((doc) => doc.ingestStatus === "READY")).toBe(true);

    const failed = await mockClient.listDocuments({ reviewStatus: "DRAFT", ingestStatus: "FAILED" });
    expect(failed.items.some((doc) => doc.id === 4)).toBe(true);
  });

  it("文档详情包含版本历史", async () => {
    const detail = await mockClient.getDocument(1);
    expect(detail.versions.length).toBeGreaterThanOrEqual(2);
    expect(detail.versions[0].versionNo).toBeGreaterThan(detail.versions[1].versionNo);
  });

  it("搜索按关键字过滤", async () => {
    const result = await mockClient.search({ keyword: "分布式事务", page: 1, size: 20 });
    expect(result.total).toBeGreaterThan(0);
    expect(result.items.every((item) => item.fileName.includes("分布式事务"))).toBe(true);
  });

  it("未知资源返回明确错误", async () => {
    await expect(mockClient.getDocument(99999)).rejects.toThrow("文档不存在");
  });
});
