import type { AuthApi } from "@/api-client/contracts/auth";
import type { LoginInput } from "@/api-client/types";
import { db, delay } from "@/mocks/db";

export const authApi: AuthApi = {
  async getCurrentUser() {
    await delay();
    return db.currentUser;
  },

  async login(input: LoginInput) {
    await delay(300);
    // mock 模式：任意非空账号密码均可登录（真实登录由后端 POST /api/v1/auth/login 校验）
    if (!input.username.trim() || !input.password) {
      throw new Error("用户名或密码错误");
    }
    return db.currentUser;
  },
};
