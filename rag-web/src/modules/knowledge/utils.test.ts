import { describe, expect, it } from "vitest";
import { formatBytes, formatDateTime, statusText } from "./utils";

describe("knowledge utils", () => {
  it("formatBytes should format file sizes by unit", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(2048)).toBe("2.0 KB");
  });

  it("statusText should map known statuses", () => {
    expect(statusText("success")).toBe("完成");
    expect(statusText("running")).toBe("处理中");
  });

  it("formatDateTime should keep invalid dates unchanged", () => {
    expect(formatDateTime("not-a-date")).toBe("not-a-date");
  });
});
