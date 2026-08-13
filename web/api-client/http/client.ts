/**
 * 真实 HTTP transport 的 axios 封装。
 *
 * - baseURL：由 `config/env.ts` 统一读取 API 地址和前缀。
 * - 请求拦截器注入 `Authorization: Bearer <accessToken>`（token 存于内存，见 lib/auth）。
 * - 响应拦截器：401 且非认证端点 → 单飞刷新（POST /auth/refresh，靠 HttpOnly cookie）→ 重试一次；
 *   刷新失败则清会话并跳 /login。`withCredentials: true` 使 refresh 请求携带 cookie。
 * - 统一信封解壳：后端返回 `{ code, message, data }`，code="0" 为成功，非零抛 {@link ApiError}。
 *
 * 页面与组件只依赖 api-client 接口，本模块细节不向外暴露。
 */
import axios, { type AxiosError, type AxiosRequestConfig } from "axios";

import { ApiError } from "@/api-client/http/errors";
import { buildApiUrl, publicEnv } from "@/config/env";
import { clearSession, getAccessToken, setAuth } from "@/lib/auth";

/** 后端统一响应信封。 */
interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  requestId?: string;
}

/** 数组查询参数逗号拼接（对齐 OpenAPI explode:false / Spring List 解析）。 */
function serializeParams(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value)) {
      if (value.length > 0) search.set(key, value.join(","));
    } else {
      search.set(key, String(value));
    }
  }
  return search.toString();
}

export const http = axios.create({
  baseURL: publicEnv.apiUrl,
  timeout: 15_000,
  withCredentials: true,
  headers: { "Content-Type": "application/json" },
  paramsSerializer: { serialize: serializeParams },
});

export { buildApiUrl };

/** 将任意错误归一化为 ApiError（供页面 catch 统一展示）。 */
function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error;
  const axiosError = error as AxiosError<ApiEnvelope<unknown>>;
  const status = axiosError?.response?.status;
  const body = axiosError?.response?.data;
  if (body && typeof body === "object" && "code" in body && "message" in body) {
    return new ApiError(body.message, {
      status,
      code: body.code,
      requestId: body.requestId,
    });
  }
  if (axiosError?.code === "ECONNABORTED") {
    return new ApiError("请求超时，请稍后重试", { status, code: "E-TIMEOUT" });
  }
  const message = axiosError?.message ?? "网络连接失败";
  return new ApiError(message, { status, code: "E-NET" });
}

/** 认证端点自身（登录/刷新）401 不触发刷新，防止死循环。 */
function isAuthEndpoint(url?: string): boolean {
  return !!url && (url.includes("/auth/login") || url.includes("/auth/refresh"));
}

// ---------- 刷新单飞：并发 401 只发一次 /auth/refresh ----------

let refreshPromise: Promise<boolean> | null = null;

async function performRefresh(): Promise<boolean> {
  try {
    const resp = await http.post<ApiEnvelope<{ accessToken: string; expiresIn: number }>>(
      "/auth/refresh",
      null,
      { withCredentials: true },
    );
    const data = unwrapEnvelope(resp.data);
    setAuth(data.accessToken, data.expiresIn);
    return true;
  } catch {
    clearSession();
    return false;
  }
}

/** 单飞刷新：成功后写入新 access token；失败清会话。认证端点调用方直接依赖返回值。 */
export function tryRefreshTokens(): Promise<boolean> {
  refreshPromise ??= performRefresh().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

http.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token && !isAuthEndpoint(config.url)) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const apiError = toApiError(error);
    if (apiError.isUnauthorized && original && !isAuthEndpoint(original.url) && !original._retried) {
      original._retried = true;
      const ok = await tryRefreshTokens();
      if (ok) {
        // 重试经请求拦截器自动注入新 Bearer（刷新后 getAccessToken() 已更新）
        return http.request(original);
      }
      // 刷新失败：清会话并回登录页（保留当前路径，登录后回跳）
      if (typeof window !== "undefined") {
        const from = window.location.pathname + window.location.search;
        window.location.assign(`/login?from=${encodeURIComponent(from)}`);
      }
    }
    return Promise.reject(apiError);
  },
);

function unwrapEnvelope<T>(body: ApiEnvelope<T> | T | undefined): T {
  if (body == null) return undefined as T;
  if (typeof body === "object" && "code" in (body as object) && "data" in (body as object)) {
    const envelope = body as ApiEnvelope<T>;
    if (envelope.code !== "0") {
      throw new ApiError(envelope.message, { code: envelope.code, requestId: envelope.requestId });
    }
    return envelope.data;
  }
  // 非信封响应（如 204 空体）直接透传
  return body as T;
}

/** JSON 接口：解壳后直接返回业务数据。 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<ApiEnvelope<T> | T>(config);
  return unwrapEnvelope<T>(response.data);
}

/** 无返回体接口（204）：成功即 resolve。 */
export async function requestVoid(config: AxiosRequestConfig): Promise<void> {
  await http.request(config);
}

/** 二进制接口（下载 / 导出）。 */
export async function requestBlob(config: AxiosRequestConfig): Promise<Blob> {
  const response = await http.request<Blob>({ ...config, responseType: "blob" });
  return response.data;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isTaskTerminal(status: string | undefined): boolean {
  return status === "SUCCEEDED" || status === "FAILED" || status === "CANCELLED";
}

/**
 * 轮询异步任务直到终态（克隆/上传/解析等后端 202 + Task 的接口）。
 * 超时抛 {@link ApiError}，页面提示去任务中心查看。
 */
export async function waitForTask(taskId: string | number, timeoutMs = 8000): Promise<import("@/api-client/types").Task> {
  const id = String(taskId);
  const deadline = Date.now() + timeoutMs;
  let lastStatus: string | undefined;
  while (Date.now() < deadline) {
    // eslint-disable-next-line no-await-in-loop
    const task = await request<import("@/api-client/types").Task>({ method: "GET", url: `/tasks/${id}` });
    lastStatus = task.status;
    if (task.status === "SUCCEEDED") return task;
    if (task.status === "FAILED" || task.status === "CANCELLED") {
      throw new ApiError(task.message ?? `任务执行失败（${task.status}）`, { code: "E-TASK" });
    }
    await sleep(500);
  }
  throw new ApiError(`任务处理超时（${lastStatus ?? "未知"}），请稍后在任务中心查看进度`, {
    code: "E-TASK-TIMEOUT",
  });
}
