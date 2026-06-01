import { PageResult, request } from "../../../services/http";
import {
  KnowledgeBase,
  KnowledgeChunk,
  KnowledgeDocument,
  UploadDocumentParams
} from "../types";

export function fetchKnowledgeBases() {
  return request<PageResult<KnowledgeBase>>("/knowledge-base?current=1&size=50");
}

export function createKnowledgeBase(params: {
  name: string;
  embeddingModel: string;
  collectionName: string;
}) {
  return request<void>("/knowledge-base", {
    method: "POST",
    body: JSON.stringify(params)
  });
}

export function updateKnowledgeBase(kbId: string, params: { name: string }) {
  return request<void>(`/knowledge-base/${kbId}`, {
    method: "PUT",
    body: JSON.stringify(params)
  });
}

export function deleteKnowledgeBase(kbId: string) {
  return request<void>(`/knowledge-base/${kbId}`, {
    method: "DELETE"
  });
}

export function fetchKnowledgeBaseDetail(kbId: string) {
  return request<KnowledgeBase>(`/knowledge-base/${kbId}`);
}

export function fetchKnowledgeDocuments(kbId: string) {
  return request<PageResult<KnowledgeDocument>>(`/knowledge-base/${kbId}/docs?current=1&size=50`);
}

export function uploadKnowledgeDocument(params: UploadDocumentParams) {
  const formData = new FormData();
  formData.set("sourceType", params.sourceType);
  formData.set("scheduleEnabled", String(params.scheduleEnabled));
  formData.set("processMode", params.processMode);
  formData.set("chunkStrategy", params.chunkStrategy);
  formData.set("chunkConfig", params.chunkConfig);

  if (params.file) {
    formData.set("file", params.file);
  }
  if (params.sourceLocation) {
    formData.set("sourceLocation", params.sourceLocation);
  }
  if (params.scheduleCron) {
    formData.set("scheduleCron", params.scheduleCron);
  }
  if (params.pipelineId) {
    formData.set("pipelineId", params.pipelineId);
  }

  return request<KnowledgeDocument>(`/knowledge-base/${params.kbId}/docs/upload`, {
    method: "POST",
    body: formData
  });
}

export function startDocumentChunk(docId: string) {
  return request<void>(`/knowledge-base/docs/${docId}/chunk`, {
    method: "POST"
  });
}

export function deleteKnowledgeDocument(docId: string) {
  return request<void>(`/knowledge-base/docs/${docId}`, {
    method: "DELETE"
  });
}

export function fetchKnowledgeDocumentDetail(docId: string) {
  return request<KnowledgeDocument>(`/knowledge-base/docs/${docId}`);
}

export function updateKnowledgeDocument(
  docId: string,
  params: {
    docName?: string;
    enabled?: boolean;
    scheduleEnabled?: boolean;
    scheduleCron?: string;
    chunkStrategy?: string;
    chunkConfig?: string;
  }
) {
  return request<void>(`/knowledge-base/docs/${docId}`, {
    method: "PUT",
    body: JSON.stringify(params)
  });
}

export function fetchKnowledgeChunks(docId: string) {
  return request<KnowledgeChunk[]>(`/knowledge-base/docs/${docId}/chunks`);
}

export function createKnowledgeChunk(docId: string, params: { content: string; index?: number | null; chunkId?: string }) {
  return request<KnowledgeChunk>(`/knowledge-base/docs/${docId}/chunks`, {
    method: "POST",
    body: JSON.stringify(params)
  });
}

export function updateKnowledgeChunk(docId: string, chunkId: string, params: { content: string }) {
  return request<void>(`/knowledge-base/docs/${docId}/chunks/${chunkId}`, {
    method: "PUT",
    body: JSON.stringify(params)
  });
}

export function deleteKnowledgeChunk(docId: string, chunkId: string) {
  return request<void>(`/knowledge-base/docs/${docId}/chunks/${chunkId}`, {
    method: "DELETE"
  });
}
