export type ApiResult<T> = {
  code: string;
  data: T;
  message: string;
  requestId?: string | null;
  success: boolean;
};

export type PageResult<T> = {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
};

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

export type LoginResponse = {
  token: string;
  role?: string;
  avatar?: string;
  userId?: string;
};

export type Conversation = {
  id: string;
  userId: string;
  title?: string;
  description?: string;
  deepThinking?: number;
  pinned?: number;
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

const API_PREFIX = "/api";
const TOKEN_KEY = "rag-web:s-token";

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY) ?? "";
}

export function setStoredToken(token: string) {
  if (token.trim()) {
    localStorage.setItem(TOKEN_KEY, token.trim());
    return;
  }
  localStorage.removeItem(TOKEN_KEY);
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = getStoredToken();

  if (token) {
    headers.set("s-token", token);
  }

  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const response = await fetch(`${API_PREFIX}${path}`, {
    ...init,
    headers
  });

  const contentType = response.headers.get("Content-Type") ?? "";
  const payload = contentType.includes("application/json")
    ? ((await response.json()) as ApiResult<T>)
    : ({ success: response.ok, data: undefined, message: await response.text() } as ApiResult<T>);

  if (!response.ok || payload.success === false) {
    throw new Error(payload.message || `请求失败：${response.status}`);
  }

  return payload.data;
}

export function login(userName: string, password: string) {
  return request<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ userName, password })
  });
}

export function logout() {
  return request<void>("/auth/logout", {
    method: "POST"
  });
}

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

export function fetchConversations(userId: string) {
  return request<Conversation[]>(`/conversation?userId=${encodeURIComponent(userId)}`);
}

export type ChatHandlers = {
  onOpen?: () => void;
  onTask?: (taskId: string) => void;
  onCancelled?: (message: string) => void;
  onThinking?: (text: string) => void;
  onToken: (text: string) => void;
  onError: (message: string) => void;
  onDone: () => void;
};

function extractSseText(raw: string) {
  const trimmed = raw.trim();
  if (!trimmed || trimmed === "[DONE]") {
    return "";
  }
  try {
    const json = JSON.parse(trimmed) as Record<string, unknown>;
    return String(json.content ?? json.data ?? json.message ?? json.text ?? "");
  } catch {
    return trimmed;
  }
}

export async function streamChat(
  params: { userQuestion: string; conversationId: string; deepThinking: boolean },
  handlers: ChatHandlers,
  signal: AbortSignal
) {
  const token = getStoredToken();
  const query = new URLSearchParams({
    userQuestion: params.userQuestion,
    conversationId: params.conversationId,
    deepThinking: String(params.deepThinking)
  });
  const headers = new Headers();

  if (token) {
    headers.set("s-token", token);
  }

  const response = await fetch(`${API_PREFIX}/rag/v1/chat?${query.toString()}`, {
    headers,
    signal
  });

  if (!response.ok || !response.body) {
    throw new Error(`聊天请求失败：${response.status}`);
  }

  handlers.onOpen?.();

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";
  let doneTriggered = false;

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const events = buffer.split(/\n\n/);
    buffer = events.pop() ?? "";

    for (const event of events) {
      const lines = event.split(/\n/);
      const dataLines = lines
        .filter((line) => line.startsWith("data:"))
        .map((line) => line.replace(/^data:\s?/, ""));
      const eventName = lines
        .find((line) => line.startsWith("event:"))
        ?.replace(/^event:\s?/, "")
        .trim();
      const text = extractSseText(dataLines.join("\n"));

      if (eventName === "task") {
        if (text) {
          handlers.onTask?.(text);
        }
        continue;
      }
      if (eventName === "cancel") {
        handlers.onCancelled?.(text || "当前聊天请求已取消");
        continue;
      }
      if (eventName === "thinking") {
        if (text) {
          handlers.onThinking?.(text);
        }
        continue;
      }
      if (eventName === "error") {
        handlers.onError(text || "服务端返回错误");
        continue;
      }
      if (eventName === "done" || dataLines.join("").trim() === "[DONE]") {
        if (!doneTriggered) {
          doneTriggered = true;
          handlers.onDone();
        }
        continue;
      }
      if (eventName && eventName !== "message") {
        continue;
      }
      if (text) {
        handlers.onToken(text);
      }
    }
  }

  if (!doneTriggered) {
    handlers.onDone();
  }
}

export function stopChatTask(taskId: string) {
  return request<void>(`/rag/v1/stop?taskId=${encodeURIComponent(taskId)}`, {
    method: "POST"
  });
}
