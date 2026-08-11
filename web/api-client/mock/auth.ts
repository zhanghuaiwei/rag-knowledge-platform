import type { AuthApi } from "@/api-client/contracts/auth";
import type { LoginInput } from "@/api-client/types";
import { clearSession, getAccessToken, setAuth } from "@/lib/auth";
import { db, delay } from "@/mocks/db";

export const authApi: AuthApi = {
  async getCurrentUser() {
    await delay();
    // mock 模式要求先登录（内存 access token），与真实行为一致
    if (!getAccessToken()) {
      throw new Error("登录已过期，请重新登录");
    }
    return db.currentUser;
  },

  async login(input: LoginInput) {
    await delay(300);
    // mock 模式：任意非空账号密码均可登录；置占位 access token 模拟登录态
    if (!input.username.trim() || !input.password) {
      throw new Error("用户名或密码错误");
    }
    setAuth("mock-token", 3600);
    return db.currentUser;
  },

  async logout() {
    await delay();
    clearSession();
  },
};
