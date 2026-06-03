import { describe, expect, it } from "vitest";

describe("chat.css layout contracts", () => {
  it("should keep the sidebar account footer pinned while conversation history scrolls", async () => {
    // @ts-expect-error 项目未引入 Node 类型；该测试仅在 Vitest 的 Node 运行时读取本地 CSS。
    const { readFileSync } = await import("node:fs");
    const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
    const css = readFileSync(`${cwd}/src/styles/pages/chat.css`, "utf-8");

    expect(css).toContain("height: 100vh;");
    expect(css).toContain("overflow: hidden;");
    expect(css).toContain("flex-shrink: 0;");
  });
});
