export type Conversation = {
  id: string;
  userId: string;
  title?: string;
  description?: string;
  deepThinking?: number;
  pinned?: number;
};

export type MemoryQueryMessage = {
  role: "user" | "assistant" | "system" | string;
  content: string;
};

export type ChatMessage = {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  thinkingContent?: string;
  thinkingCollapsed?: boolean;
  thinkingStartedAt?: number;
  thinkingDurationSeconds?: number;
};

export type ChatHandlers = {
  onOpen?: () => void;
  onTask?: (taskId: string) => void;
  onCancelled?: (message: string) => void;
  onThinking?: (text: string) => void;
  onToken: (text: string) => void;
  onError: (message: string) => void;
  onDone: () => void;
};
