import { describe, expect, it } from "vitest";

import { GET } from "./route";

describe("GET /api/health", () => {
  it("返回脚手架状态", async () => {
    const res = await GET();
    const body = await res.json();
    expect(body.status).toBe("ok");
    expect(body.service).toBe("ragkb-web");
    expect(body.phase).toBe("scaffold");
  });
});
