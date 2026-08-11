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

describe("mock client 写操作", () => {
  it("创建知识库后出现在列表且当前用户为所有者", async () => {
    const created = await mockClient.createKb({
      name: "测试知识库",
      visibility: "PRIVATE",
      requiresReview: true,
    });
    const page = await mockClient.listKbs({ page: 1, size: 50 });
    expect(page.items.some((kb) => kb.id === created.id)).toBe(true);
    expect(created.role).toBe("OWNER");
    expect(created.members[0].role).toBe("OWNER");
  });

  it("重复名称创建被拒绝", async () => {
    await mockClient.createKb({ name: "产品研发知识库", visibility: "PRIVATE" }).catch((err) => {
      expect(err.message).toContain("已存在");
    });
  });

  it("审核通过传播到文档状态并从队列移除", async () => {
    await mockClient.approveReviews([9], "确认发布");
    const detail = await mockClient.getDocument(9);
    expect(detail.reviewStatus).toBe("PUBLISHED");
    const queue = await mockClient.listReviews({ page: 1, size: 50 });
    expect(queue.items.some((item) => item.documentId === 9)).toBe(false);
  });

  it("驳回必须填写审核意见", async () => {
    await expect(mockClient.rejectReviews([3], "")).rejects.toThrow("驳回必须填写审核意见");
  });

  it("收藏切换会同步收藏列表", async () => {
    await mockClient.toggleFavorite(1);
    const favorites = await mockClient.listFavorites({ page: 1, size: 50 });
    expect(favorites.items.some((f) => f.documentId === 1)).toBe(true);
    const detail = await mockClient.getDocument(1);
    expect(detail.isFavorite).toBe(true);
  });

  it("删除文档后详情不可达", async () => {
    await mockClient.deleteDocument(6);
    await expect(mockClient.getDocument(6)).rejects.toThrow("文档不存在");
  });

  it("重试超过 3 次被锁定", async () => {
    // 重置为失败态：先重试一次用于构造可重试文档（直接用已失败文档 4）
    for (let i = 0; i < 3; i += 1) {
      await mockClient.retryIngest(4);
    }
    await expect(mockClient.retryIngest(4)).rejects.toThrow("3 次");
  });

  it("创建会话后出现在会话列表", async () => {
    const session = await mockClient.createChatSession({ kbIds: [1, 2] });
    const list = await mockClient.listChatSessions({ page: 1, size: 50 });
    expect(list.items.some((s) => s.id === session.id)).toBe(true);
    expect(session.kbIds).toEqual([1, 2]);
  });

  it("发送消息后消息持久化到会话", async () => {
    await mockClient.sendChatMessage({ sessionId: 102, content: "追加提问", kbIds: [1] });
    const messages = await mockClient.listChatMessages(102);
    const last = messages[messages.length - 1];
    expect(last.role).toBe("ASSISTANT");
  });
});
