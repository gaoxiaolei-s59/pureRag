import { buildApiUrl, request } from "../../../services/http";
import { getStoredToken } from "../../auth/storage";
import { ChatHandlers, Conversation, MemoryQueryMessage } from "../types";

export function fetchConversations(userId: string) {
  return request<Conversation[]>(`/conversation?userId=${encodeURIComponent(userId)}`);
}

export function fetchConversationMessages(conversationId: string) {
  return request<MemoryQueryMessage[]>(`/memory/v1/query?conversionId=${encodeURIComponent(conversationId)}`);
}

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

  const response = await fetch(buildApiUrl(`/rag/v1/chat?${query.toString()}`), {
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
