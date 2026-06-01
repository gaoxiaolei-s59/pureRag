import { describe, expect, it } from "vitest";
import { filterIntentNodes } from "./selectors";
import type { IntentNode } from "./types";

describe("intent selectors", () => {
  const nodes: IntentNode[] = [
    { recordId: "1", id: "root", name: "订单", level: "DOMAIN", kind: "KB", enabled: 1 },
    { recordId: "2", id: "child", name: "退款", description: "退款规则", level: "CATEGORY", kind: "SYSTEM", enabled: 1 }
  ];

  it("filters by keyword", () => {
    expect(filterIntentNodes(nodes, "退款", "all", "all").map((item) => item.id)).toEqual(["child"]);
  });

  it("filters by level and kind", () => {
    expect(filterIntentNodes(nodes, "", "CATEGORY", "SYSTEM").map((item) => item.id)).toEqual(["child"]);
  });
});
