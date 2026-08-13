import { afterAll, beforeEach, describe, expect, it, vi } from "vitest";

const originalEnv = process.env;

beforeEach(() => {
  vi.resetModules();
  process.env = { ...originalEnv };
  const testEnv = process.env as Record<string, string | undefined>;
  delete testEnv.NEXT_PUBLIC_USE_MOCK;
  delete testEnv.NEXT_PUBLIC_API_BASE_URL;
  delete testEnv.NEXT_PUBLIC_API_PREFIX;
  delete testEnv.NEXT_PUBLIC_MINIO_BASE_URL;
});

afterAll(() => {
  process.env = originalEnv;
});

describe("public environment configuration", () => {
  it("uses safe local defaults", async () => {
    const { buildApiUrl, buildMinioUrl, publicEnv } = await import("@/config/env");

    expect(publicEnv.useMock).toBe(true);
    expect(buildApiUrl("/auth/login")).toBe("http://localhost:8080/api/v1/auth/login");
    expect(buildMinioUrl("documents/example.pdf")).toBe("http://localhost:9000/documents/example.pdf");
  });

  it("normalizes environment-specific endpoints", async () => {
    const testEnv = process.env as Record<string, string | undefined>;
    testEnv.NEXT_PUBLIC_USE_MOCK = "false";
    testEnv.NEXT_PUBLIC_API_BASE_URL = "https://api.example.com/";
    testEnv.NEXT_PUBLIC_API_PREFIX = "platform/v2/";
    testEnv.NEXT_PUBLIC_MINIO_BASE_URL = "https://objects.example.com/";

    const { buildApiUrl, buildMinioUrl, publicEnv } = await import("@/config/env");

    expect(publicEnv.useMock).toBe(false);
    expect(publicEnv.apiUrl).toBe("https://api.example.com/platform/v2");
    expect(buildApiUrl("/health")).toBe("https://api.example.com/platform/v2/health");
    expect(buildMinioUrl("/tenant-a/file.pdf")).toBe("https://objects.example.com/tenant-a/file.pdf");
  });
});
