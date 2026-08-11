/**
 * JWT 集成行为测试（自定义 axios adapter，模拟内置 settle：非 2xx 抛 AxiosError）。
 * - 请求拦截器注入 Bearer；
 * - 401（非认证端点）触发单飞刷新并重试一次；
 * - 登录/刷新端点自身 401 不触发刷新（防死循环）；
 * - 刷新失败清会话（node 环境无 window，跳转分支被跳过）。
 */
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { AxiosError, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";

import { http } from "@/api-client/http/client";
import { clearSession, getAccessToken, setAuth } from "@/lib/auth";

const originalAdapter = http.defaults.adapter;

/** 模拟 axios 内置 settle：2xx resolve，非 2xx 抛 AxiosError。 */
function settled(config: InternalAxiosRequestConfig, status: number, data: unknown, code = "0"): AxiosResponse {
  const response: AxiosResponse = {
    data: { code, message: status === 200 ? "OK" : "error", data },
    status,
    statusText: status === 200 ? "OK" : "error",
    headers: {},
    config,
  };
  if (status >= 200 && status < 300) return response;
  throw new AxiosError(
    `Request failed with status code ${status}`,
    AxiosError.ERR_BAD_RESPONSE,
    config,
    null,
    response,
  );
}

function readAuthorizationHeader(config: { headers?: unknown }): string | undefined {
  const headers = config.headers as { Authorization?: string; get?: (k: string) => string | undefined } | undefined;
  return headers?.Authorization ?? headers?.get?.("Authorization");
}

beforeEach(() => {
  clearSession();
});

afterEach(() => {
  http.defaults.adapter = originalAdapter as never;
});

describe("JWT http 集成", () => {
  it("请求拦截器为受保护请求注入 Bearer", async () => {
    setAuth("token-abc", 3600);
    const sent: (string | undefined)[] = [];
    http.defaults.adapter = (async (config) => {
      sent.push(readAuthorizationHeader(config));
      return settled(config, 200, null);
    }) as AxiosAdapter;

    await http.get("/kbs");
    expect(sent).toEqual(["Bearer token-abc"]);
  });

  it("401 触发单飞刷新并重试一次", async () => {
    setAuth("old-token", 3600);
    let refreshCalls = 0;
    let dataCalls = 0;
    http.defaults.adapter = (async (config) => {
      if ((config.url ?? "").includes("/auth/refresh")) {
        refreshCalls += 1;
        return settled(config, 200, { accessToken: "new-token", expiresIn: 3600 });
      }
      dataCalls += 1;
      return dataCalls === 1 ? settled(config, 401, null, "E-1001") : settled(config, 200, { ok: true });
    }) as AxiosAdapter;

    const resp = await http.get("/kbs");
    expect(dataCalls).toBe(2);
    expect(refreshCalls).toBe(1);
    expect(getAccessToken()).toBe("new-token");
    expect((resp.data as { data: { ok: boolean } }).data.ok).toBe(true);
  });

  it("登录/刷新端点自身 401 不触发刷新（防死循环）", async () => {
    let refreshCalls = 0;
    http.defaults.adapter = (async (config) => {
      if ((config.url ?? "").includes("/auth/refresh")) {
        refreshCalls += 1;
        return settled(config, 200, { accessToken: "x", expiresIn: 3600 });
      }
      return settled(config, 401, null, "E-1001");
    }) as AxiosAdapter;

    await expect(http.post("/auth/login", { username: "a", password: "b" })).rejects.toMatchObject({ code: "E-1001" });
    expect(refreshCalls).toBe(0);
  });

  it("刷新失败清会话", async () => {
    setAuth("old-token", 3600);
    http.defaults.adapter = (async (config) => settled(config, 401, null, "E-1001")) as AxiosAdapter;

    await expect(http.get("/kbs")).rejects.toMatchObject({ code: "E-1001" });
    expect(getAccessToken()).toBeNull();
  });
});
