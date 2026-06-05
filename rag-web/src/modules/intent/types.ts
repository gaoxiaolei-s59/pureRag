export type IntentNode = {
  recordId?: string;
  id: string;
  kbId?: string;
  name: string;
  description?: string;
  examples?: string[];
  level?: "DOMAIN" | "CATEGORY" | "TOPIC" | string;
  parentId?: string | null;
  collectionName?: string;
  mcpToolId?: string;
  kind?: "KB" | "MCP" | "SYSTEM" | string;
  topK?: number;
  sortOrder?: number;
  enabled?: number;
  promptSnippet?: string;
  promptTemplate?: string;
  paramPromptTemplate?: string;
  fullPath?: string;
  children?: string[];
};

export type IntentNodePayload = {
  kbId?: string;
  intentCode: string;
  name: string;
  level: number;
  parentCode?: string;
  description?: string;
  examples?: string[];
  collectionName?: string;
  mcpToolId?: string;
  topK?: number | null;
  kind: number;
  sortOrder?: number;
  enabled?: number;
  promptSnippet?: string;
  promptTemplate?: string;
  paramPromptTemplate?: string;
};

export type IntentTreeNode = IntentNode & { treeChildren: IntentTreeNode[] };
