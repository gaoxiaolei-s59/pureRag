import { IntentNode } from "./types";

export function filterIntentNodes(
  intentNodes: IntentNode[],
  search: string,
  levelFilter: string,
  kindFilter: string
) {
  const keyword = search.trim().toLowerCase();
  return intentNodes.filter((item) => {
    const matchesKeyword =
      !keyword ||
      [item.name, item.id, item.description, item.fullPath, item.collectionName, item.kind, item.level]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(keyword);
    const matchesLevel = levelFilter === "all" || item.level === levelFilter;
    const matchesKind = kindFilter === "all" || item.kind === kindFilter;
    return matchesKeyword && matchesLevel && matchesKind;
  });
}
