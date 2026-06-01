import { KnowledgeDocument } from "./types";

export function filterDocuments(
  documents: KnowledgeDocument[],
  search: string,
  statusFilter: string
) {
  const keyword = search.trim().toLowerCase();
  return documents.filter((item) => {
    const matchesKeyword =
      !keyword ||
      `${item.docName} ${item.sourceLocation ?? ""} ${item.fileType ?? ""}`.toLowerCase().includes(keyword);
    const matchesStatus = statusFilter === "all" || (item.status ?? "unknown") === statusFilter;
    return matchesKeyword && matchesStatus;
  });
}
