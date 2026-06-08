export type KnowledgeBase = {
  id: string;
  name: string;
  embeddingModel: string;
  collectionName: string;
  documentCount?: number;
  createdBy?: string;
  createTime?: string;
  updateTime?: string;
};

export type KnowledgeBaseInfo = {
  id: string;
  name: string;
};

export type KnowledgeDocument = {
  id: string;
  kbId: string;
  docName: string;
  sourceType: "file" | "url" | string;
  sourceLocation?: string;
  scheduleEnabled?: number;
  scheduleCron?: string;
  enabled?: number;
  chunkCount?: number;
  fileUrl?: string;
  fileType?: string;
  fileSize?: number;
  chunkStrategy?: string;
  processMode?: string;
  chunkConfig?: string;
  pipelineId?: string;
  status?: string;
  createdBy?: string;
  updatedBy?: string;
  createTime?: string;
  updateTime?: string;
};

export type KnowledgeChunk = {
  id: string;
  kbId: string;
  docId: string;
  chunkIndex?: number;
  content: string;
  contentHash?: string;
  charCount?: number;
  tokenCount?: number;
  enabled?: number;
  createTime?: string;
  updateTime?: string;
};

export type UploadDocumentParams = {
  kbId: string;
  sourceType: "file" | "url";
  file?: File | null;
  sourceLocation?: string;
  scheduleEnabled: boolean;
  scheduleCron?: string;
  processMode: "chunk" | "pipeline";
  chunkStrategy: string;
  chunkConfig: string;
  pipelineId?: string;
};
