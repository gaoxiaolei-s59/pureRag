import { describe, expect, it } from "vitest";
import { buildKnowledgeDocumentUpdatePayload, createKnowledgeDocumentFormState } from "./documentForm";
import type { KnowledgeDocument } from "./types";

describe("knowledge document form", () => {
  it("createKnowledgeDocumentFormState should map document detail into editable state", () => {
    const detail: KnowledgeDocument = {
      id: "doc-1",
      kbId: "kb-1",
      docName: "产品手册",
      sourceType: "file",
      enabled: 1,
      scheduleEnabled: 1,
      scheduleCron: "0 0/15 * * * ?",
      chunkStrategy: "fixed_size",
      chunkConfig: "{\"chunkSize\":256}"
    };

    const form = createKnowledgeDocumentFormState(detail);

    expect(form.docFormName).toBe("产品手册");
    expect(form.docFormEnabled).toBe(true);
    expect(form.docFormScheduleEnabled).toBe(true);
    expect(form.docFormScheduleCron).toBe("0 0/15 * * * ?");
    expect(form.docFormChunkConfig).toBe("{\"chunkSize\":256}");
  });

  it("buildKnowledgeDocumentUpdatePayload should trim name and clear cron when disabled", () => {
    const payload = buildKnowledgeDocumentUpdatePayload({
      docFormName: " 产品手册 ",
      docFormEnabled: false,
      docFormScheduleEnabled: false,
      docFormScheduleCron: "0 0/15 * * * ?",
      docFormChunkStrategy: "fixed_size",
      docFormChunkConfig: "{\"chunkSize\":512}"
    });

    expect(payload).toEqual({
      docName: "产品手册",
      enabled: false,
      scheduleEnabled: false,
      scheduleCron: "",
      chunkStrategy: "fixed_size",
      chunkConfig: "{\"chunkSize\":512}"
    });
  });
});
