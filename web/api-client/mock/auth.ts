import type { AuthApi } from "@/api-client/contracts/auth";
import { db, delay } from "@/mocks/db";

export const authApi: AuthApi = {
  async getCurrentUser() {
    await delay();
    return db.currentUser;
  },
};
