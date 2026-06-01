import { useMemo, useRef, useState } from "react";
import { getStoredUserId } from "../../auth/storage";
import {
  fetchConversationMessages,
  fetchConversations,
  stopChatTask,
  streamChat
} from "../services/chat";
import { ChatMessage, Conversation } from "../types";

function createConversationId() {
  return crypto.randomUUID().replaceAll("-", "");
}

function createMessageId() {
  return crypto.randomUUID();
}

function toChatHistoryMessage(role: string, content: string): ChatMessage {
  return {
    id: createMessageId(),
    role: role === "assistant" || role === "system" ? role : "user",
    content
  };
}

export function useChatPage() {
  const [notice, setNotice] = useState("准备就绪");
  const [conversationId, setConversationId] = useState(createConversationId());
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [conversationSearch, setConversationSearch] = useState("");
  const [question, setQuestion] = useState("");
  const [deepThinking, setDeepThinking] = useState(false);
  const [chatLoading, setChatLoading] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const taskIdRef = useRef("");
  const userId = getStoredUserId();

  function finalizeAssistantThinking(messageId: string) {
    setMessages((current) =>
      current.map((message) => {
        if (message.id !== messageId || !message.thinkingContent) {
          return message;
        }
        return {
          ...message,
          thinkingCollapsed: true,
          thinkingDurationSeconds: message.thinkingStartedAt
            ? Math.max(1, Math.round((Date.now() - message.thinkingStartedAt) / 1000))
            : message.thinkingDurationSeconds
        };
      })
    );
  }

  async function stopActiveChat(showStoppedNotice = true) {
    const taskId = taskIdRef.current;
    let stopFailed = false;
    if (taskId) {
      try {
        await stopChatTask(taskId);
      } catch (error) {
        stopFailed = true;
        setNotice(error instanceof Error ? error.message : "停止聊天失败");
      }
    }
    abortRef.current?.abort();
    taskIdRef.current = "";
    setChatLoading(false);
    if (showStoppedNotice && !stopFailed) {
      setNotice("已停止当前聊天请求");
    }
  }

  async function refreshConversations(nextUserId = userId) {
    if (!nextUserId) {
      setConversations([]);
      return;
    }
    try {
      const records = await fetchConversations(nextUserId);
      setConversations(records ?? []);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新会话失败");
    }
  }

  async function selectConversation(conversation: Conversation) {
    await stopActiveChat(false);
    setConversationId(conversation.id);
    setQuestion("");
    setDeepThinking(conversation.deepThinking === 1);
    setChatLoading(false);
    try {
      const historyMessages = await fetchConversationMessages(conversation.id);
      setMessages((historyMessages ?? []).map((item) => toChatHistoryMessage(item.role, item.content)));
      setNotice(`已切换到会话：${conversation.title || conversation.id}`);
    } catch (error) {
      setMessages([]);
      setNotice(error instanceof Error ? error.message : "加载历史会话失败");
    }
  }

  function newConversation() {
    void stopActiveChat(false);
    setConversationId(createConversationId());
    setMessages([]);
    setQuestion("");
    setChatLoading(false);
    setNotice("已创建新对话");
  }

  async function sendMessage() {
    const userQuestion = question.trim();
    if (!userQuestion || chatLoading) {
      return;
    }

    await stopActiveChat(false);
    const controller = new AbortController();
    const assistantMessageId = createMessageId();
    abortRef.current = controller;
    setQuestion("");
    setChatLoading(true);
    setMessages((current) => [
      ...current,
      { id: createMessageId(), role: "user", content: userQuestion },
      {
        id: assistantMessageId,
        role: "assistant",
        content: "",
        thinkingContent: "",
        thinkingCollapsed: false
      }
    ]);

    try {
      await streamChat(
        { userQuestion, conversationId, deepThinking },
        {
          onTask: (taskId) => {
            taskIdRef.current = taskId;
          },
          onToken: (text) => {
            setMessages((current) =>
              current.map((message) =>
                message.id === assistantMessageId
                  ? { ...message, content: `${message.content}${text}` }
                  : message
              )
            );
          },
          onThinking: (text) => {
            setMessages((current) =>
              current.map((message) =>
                message.id === assistantMessageId
                  ? {
                      ...message,
                      thinkingCollapsed: false,
                      thinkingStartedAt: message.thinkingStartedAt ?? Date.now(),
                      thinkingContent: `${message.thinkingContent ?? ""}${text}`
                    }
                  : message
              )
            );
          },
          onError: (message) => {
            setNotice(message);
          },
          onCancelled: (message) => {
            finalizeAssistantThinking(assistantMessageId);
            setNotice(message);
          },
          onDone: () => {
            finalizeAssistantThinking(assistantMessageId);
            setChatLoading(false);
            taskIdRef.current = "";
            void refreshConversations();
          }
        },
        controller.signal
      );
    } catch (error) {
      if (!controller.signal.aborted) {
        setNotice(error instanceof Error ? error.message : "聊天请求失败");
      }
    } finally {
      finalizeAssistantThinking(assistantMessageId);
      taskIdRef.current = "";
      setChatLoading(false);
    }
  }

  function toggleThinking(messageId: string) {
    setMessages((current) =>
      current.map((message) =>
        message.id === messageId
          ? {
              ...message,
              thinkingCollapsed: !message.thinkingCollapsed
            }
          : message
      )
    );
  }

  const filteredConversations = useMemo(() => {
    const keyword = conversationSearch.trim().toLowerCase();
    if (!keyword) {
      return conversations;
    }
    return conversations.filter((item) =>
      `${item.title ?? ""} ${item.description ?? ""}`.toLowerCase().includes(keyword)
    );
  }, [conversationSearch, conversations]);

  return {
    userId,
    notice,
    conversationId,
    conversations,
    filteredConversations,
    conversationSearch,
    question,
    deepThinking,
    chatLoading,
    messages,
    refreshConversations,
    setConversationSearch,
    setQuestion,
    setDeepThinking,
    sendMessage,
    stopActiveChat,
    stopChat: () => stopActiveChat(true),
    selectConversation,
    newConversation,
    toggleThinking
  };
}
