import { describe, expect, it } from "vitest";
import { filterDocuments } from "./selectors";
import type { KnowledgeDocument } from "./types";

describe("knowledge selectors", () => {
  const documents: KnowledgeDocument[] = [
    { id: "1", kbId: "kb", docName: "产品说明书", sourceType: "file", fileType: "pdf", status: "success" },
    { id: "2", kbId: "kb", docName: "接口文档", sourceType: "url", sourceLocation: "https://api.example.com", status: "running" }
  ];

  it("filters documents by keyword", () => {
    expect(filterDocuments(documents, "接口", "all").map((item) => item.id)).toEqual(["2"]);
  });

  it("filters documents by status", () => {
    expect(filterDocuments(documents, "", "success").map((item) => item.id)).toEqual(["1"]);
  });
});
