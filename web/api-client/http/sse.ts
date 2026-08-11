/**
 * SSE 流式读取器。
 *
 * 后端提问接口返回 `text/event-stream`，事件约定：meta → token* → sources? → final / error
 * （03-详细设计 §9.3）。逐块读取并解析事件块，处理跨 chunk 的行拼接。
 */
import { ApiError } from "@/api-client/http/errors";

export interface SseEvent {
  event: string;
  data: string;
}

/** 流式读取 SSE，逐事件回调；遇到 error 事件抛 ApiError。 */
export async function readSse(
  url: string,
  init: RequestInit,
  onEvent: (event: SseEvent) => void,
): Promise<void> {
  const response = await fetch(url, init);
  const contentType = response.headers.get("content-type") ?? "";
  // 后端可能以 JSON 错误信封返回（如桩实现 501）：统一转为 ApiError。
  if (!response.ok || !contentType.includes("text/event-stream")) {
    const body = await parseErrorBody(response);
    throw new ApiError(body.message, { status: response.status, code: body.code, requestId: body.requestId });
  }
  if (response.body == null) {
    throw new ApiError("空响应", { status: response.status });
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    // 事件块以空行分隔（\n\n 或 \r\n\r\n）
    let sep: number;
    while ((sep = buffer.search(/\r?\n\r?\n/)) !== -1) {
      // 分隔符可能是 \n\n（2 字符）或 \r\n\r\n（4 字符）
      const separatorLen = buffer[sep] === "\r" ? 4 : 2;
      const block = buffer.slice(0, sep);
      buffer = buffer.slice(sep + separatorLen);
      dispatchBlock(block, onEvent);
    }
  }
  // 处理结尾未带空行的最后一个事件块
  if (buffer.trim().length > 0) {
    dispatchBlock(buffer, onEvent);
  }
}

function dispatchBlock(block: string, onEvent: (event: SseEvent) => void): void {
  const lines = block.split(/\r?\n/);
  let eventName = "message";
  const dataLines: string[] = [];
  for (const line of lines) {
    if (line.startsWith("event:")) {
      eventName = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice("data:".length).trimStart());
    }
  }
  if (dataLines.length === 0) return;
  const event = { event: eventName, data: dataLines.join("\n") };
  if (eventName === "error") {
    throw new ApiError(tryParseMessage(event.data) ?? "服务端处理失败", { code: "E-SSE" });
  }
  onEvent(event);
}

function tryParseMessage(data: string): string | null {
  try {
    const obj = JSON.parse(data) as { message?: string };
    return typeof obj.message === "string" ? obj.message : null;
  } catch {
    return data || null;
  }
}

async function parseErrorBody(response: Response): Promise<{ message: string; code?: string; requestId?: string }> {
  try {
    const json = (await response.json()) as { message?: string; code?: string; requestId?: string };
    return {
      message: json.message ?? `请求失败（HTTP ${response.status}）`,
      code: json.code,
      requestId: json.requestId,
    };
  } catch {
    return { message: `请求失败（HTTP ${response.status}）` };
  }
}
