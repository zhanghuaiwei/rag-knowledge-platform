import { describe, expect, it } from "vitest";

import { formatFileSize, formatPercent, statusText } from "./format";

describe("format 工具", () => {
  it("文件大小按量级格式化", () => {
    expect(formatFileSize(512)).toBe("512 B");
    expect(formatFileSize(2048)).toBe("2.0 KB");
    expect(formatFileSize(5 * 1024 * 1024)).toBe("5.0 MB");
  });

  it("百分比保留一位小数", () => {
    expect(formatPercent(0.1234)).toBe("12.3%");
  });

  it("状态文案映射未知值时回退原值", () => {
    expect(statusText("ingest", "READY")).toEqual(["就绪", "success"]);
    expect(statusText("ingest", "UNKNOWN")).toEqual(["UNKNOWN", ""]);
  });
});
