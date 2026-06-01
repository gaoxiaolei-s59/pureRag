import { describe, expect, it } from "vitest";
import { buildIntentTree, intentKindText, intentLevelText } from "./utils";
import type { IntentNode } from "./types";

describe("intent utils", () => {
  it("buildIntentTree should group children under matching parent and sort by sortOrder", () => {
    const nodes: IntentNode[] = [
      { recordId: "2", id: "child-b", name: "子节点B", parentId: "root", sortOrder: 2 },
      { recordId: "1", id: "root", name: "根节点", sortOrder: 1 },
      { recordId: "3", id: "child-a", name: "子节点A", parentId: "root", sortOrder: 1 }
    ];

    const tree = buildIntentTree(nodes);

    expect(tree).toHaveLength(1);
    expect(tree[0].treeChildren.map((item) => item.id)).toEqual(["child-a", "child-b"]);
  });

  it("should map level and kind labels for display", () => {
    expect(intentLevelText("DOMAIN")).toBe("领域");
    expect(intentKindText("MCP")).toBe("MCP 工具");
  });
});
