/**
 * SSE 读取器单元测试：事件解析、跨 chunk 分片、多行 data、错误事件、非 SSE 响应。
 */
import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/api-client/http/errors";
import { readSse, type SseEvent } from "@/api-client/http/sse";

function sseResponse(chunks: string[]): Response {
  const encoder = new TextEncoder();
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk));
      controller.close();
    },
  });
  return new Response(stream, {
    status: 200,
    headers: { "Content-Type": "text/event-stream" },
  });
}

function jsonResponse(body: string, status = 200): Response {
  return new Response(body, { status, headers: { "Content-Type": "application/json" } });
}

describe("readSse", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("解析单个事件块", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => sseResponse(['event: meta\ndata: {"messageId":1}\n\n'])));
    const events: SseEvent[] = [];
    await readSse("http://x/chat", { method: "POST" }, (ev) => events.push(ev));
    expect(events).toEqual([{ event: "meta", data: '{"messageId":1}' }]);
  });

  it("跨 chunk 拆分时仍能完整解析多事件", async () => {
    const body = 'event: token\ndata: {"text":"你好"}\n\nevent: final\ndata: {"content":"你好","cost":0.1}\n\n';
    // 拆成小片模拟网络分片
    const parts: string[] = [];
    for (let i = 0; i < body.length; i += 7) parts.push(body.slice(i, i + 7));
    vi.stubGlobal("fetch", vi.fn(async () => sseResponse(parts)));

    const events: SseEvent[] = [];
    await readSse("http://x/chat", {}, (ev) => events.push(ev));
    expect(events).toHaveLength(2);
    expect(events[0]).toMatchObject({ event: "token" });
    expect(events[1].event).toBe("final");
    expect(JSON.parse(events[1].data)).toMatchObject({ content: "你好" });
  });

  it("多行 data 行拼接后整体为合法 JSON", async () => {
    // SSE 规范：多行 data 需重复 `data:` 前缀，用 \n 拼接
    const body = 'event: sources\ndata: {"sources":[\ndata: 1,\ndata: 2]}\n\n';
    vi.stubGlobal("fetch", vi.fn(async () => sseResponse([body])));
    const events: SseEvent[] = [];
    await readSse("http://x", {}, (ev) => events.push(ev));
    expect(JSON.parse(events[0].data)).toEqual({ sources: [1, 2] });
  });

  it("error 事件抛出 ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => sseResponse(['event: error\ndata: {"message":"boom"}\n\n'])));
    await expect(readSse("http://x", {}, () => {})).rejects.toThrow(ApiError);
  });

  it("非 SSE 响应（JSON 错误信封）抛出 ApiError", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => jsonResponse(JSON.stringify({ code: "E-1003", message: "资源不存在" }))));
    await expect(readSse("http://x", {}, () => {})).rejects.toThrow(/资源不存在/);
  });
});
