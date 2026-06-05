import { describe, expect, it } from "vitest";

describe("knowledge.css table layout contracts", () => {
  it("should prevent long table values from overlapping adjacent columns", async () => {
    // @ts-expect-error 项目未引入 Node 类型；该测试仅在 Vitest 的 Node 运行时读取本地 CSS。
    const { readFileSync } = await import("node:fs");
    const cwd = (globalThis as unknown as { process: { cwd: () => string } }).process.cwd();
    const css = readFileSync(`${cwd}/src/styles/pages/knowledge.css`, "utf-8");

    expect(css).toContain(".knowledge-table-scroll");
    expect(css).toContain("overflow-x: auto;");
    expect(css).toContain("min-width: 1120px;");
    expect(css).toContain("min-height: 54px;");
    expect(css).toContain("text-overflow: ellipsis;");
    expect(css).toContain('.dashboard-shell[data-theme="dark"] .knowledge-table-scroll .table-row');
  });
});
