import { KnowledgeDocument } from "./types";
import { DEFAULT_CHUNK_CONFIG } from "./utils";

export type KnowledgeDocumentFormState = {
  docFormName: string;
  docFormEnabled: boolean;
  docFormScheduleEnabled: boolean;
  docFormScheduleCron: string;
  docFormChunkStrategy: string;
  docFormChunkConfig: string;
};

export function createKnowledgeDocumentFormState(detail: KnowledgeDocument): KnowledgeDocumentFormState {
  return {
    docFormName: detail.docName ?? "",
    docFormEnabled: detail.enabled !== 0,
    docFormScheduleEnabled: detail.scheduleEnabled === 1,
    docFormScheduleCron: detail.scheduleCron ?? "0 0/30 * * * ?",
    docFormChunkStrategy: detail.chunkStrategy ?? "fixed_size",
    docFormChunkConfig: detail.chunkConfig ?? DEFAULT_CHUNK_CONFIG
  };
}

export function buildKnowledgeDocumentUpdatePayload(form: KnowledgeDocumentFormState) {
  return {
    docName: form.docFormName.trim(),
    enabled: form.docFormEnabled,
    scheduleEnabled: form.docFormScheduleEnabled,
    scheduleCron: form.docFormScheduleEnabled ? form.docFormScheduleCron : "",
    chunkStrategy: form.docFormChunkStrategy,
    chunkConfig: form.docFormChunkConfig
  };
}
