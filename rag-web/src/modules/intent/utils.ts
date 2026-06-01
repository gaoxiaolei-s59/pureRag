import { IntentNode, IntentTreeNode } from "./types";

export function buildIntentTree(nodes: IntentNode[]) {
  const nodeMap = new Map<string, IntentTreeNode>();
  for (const node of nodes) {
    nodeMap.set(node.id, { ...node, treeChildren: [] });
  }

  const roots: IntentTreeNode[] = [];
  for (const node of nodeMap.values()) {
    const parentId = node.parentId ?? "";
    if (parentId && nodeMap.has(parentId)) {
      nodeMap.get(parentId)?.treeChildren.push(node);
    } else {
      roots.push(node);
    }
  }

  const sortNodes = (items: IntentTreeNode[]) => {
    items.sort((left, right) => {
      const orderGap = (left.sortOrder ?? 0) - (right.sortOrder ?? 0);
      if (orderGap !== 0) {
        return orderGap;
      }
      return left.name.localeCompare(right.name, "zh-CN");
    });
    items.forEach((item) => sortNodes(item.treeChildren));
  };

  sortNodes(roots);
  return roots;
}

export function intentLevelText(level?: string) {
  const map: Record<string, string> = {
    DOMAIN: "领域",
    CATEGORY: "分类",
    TOPIC: "主题"
  };
  return level ? map[level] ?? level : "-";
}

export function intentKindText(kind?: string) {
  const map: Record<string, string> = {
    KB: "知识库",
    MCP: "MCP 工具",
    SYSTEM: "系统能力"
  };
  return kind ? map[kind] ?? kind : "-";
}

export function levelCodeFromValue(level?: string) {
  if (level === "CATEGORY") {
    return 1;
  }
  if (level === "TOPIC") {
    return 2;
  }
  return 0;
}

export function kindCodeFromValue(kind?: string) {
  if (kind === "MCP") {
    return 1;
  }
  if (kind === "SYSTEM") {
    return 2;
  }
  return 0;
}
