import { IntentNode, IntentNodePayload } from "./types";
import { kindCodeFromValue, levelCodeFromValue } from "./utils";

export type IntentFormMode = "create-root" | "create-child" | "edit";

export type IntentFormState = {
  recordId?: string;
  kbId: string;
  intentCode: string;
  name: string;
  level: number;
  parentCode: string;
  description: string;
  examplesText: string;
  collectionName: string;
  mcpToolId: string;
  topK: string;
  kind: number;
  sortOrder: string;
  enabled: boolean;
  promptSnippet: string;
  promptTemplate: string;
  paramPromptTemplate: string;
};

export function createEmptyIntentForm(): IntentFormState {
  return {
    kbId: "",
    intentCode: "",
    name: "",
    level: 0,
    parentCode: "",
    description: "",
    examplesText: "",
    collectionName: "",
    mcpToolId: "",
    topK: "",
    kind: 0,
    sortOrder: "0",
    enabled: true,
    promptSnippet: "",
    promptTemplate: "",
    paramPromptTemplate: ""
  };
}

export function createChildIntentForm(parent: IntentNode): IntentFormState {
  return {
    ...createEmptyIntentForm(),
    parentCode: parent.id,
    level: Math.min(levelCodeFromValue(parent.level) + 1, 2)
  };
}

export function createEditIntentForm(node: IntentNode): IntentFormState {
  return {
    recordId: node.recordId,
    kbId: node.kbId ?? "",
    intentCode: node.id,
    name: node.name,
    level: levelCodeFromValue(node.level),
    parentCode: node.parentId ?? "",
    description: node.description ?? "",
    examplesText: (node.examples ?? []).join("\n"),
    collectionName: node.collectionName ?? "",
    mcpToolId: node.mcpToolId ?? "",
    topK: node.topK != null ? String(node.topK) : "",
    kind: kindCodeFromValue(node.kind),
    sortOrder: node.sortOrder != null ? String(node.sortOrder) : "0",
    enabled: node.enabled === 1,
    promptSnippet: node.promptSnippet ?? "",
    promptTemplate: node.promptTemplate ?? "",
    paramPromptTemplate: node.paramPromptTemplate ?? ""
  };
}

export function buildIntentNodePayload(form: IntentFormState): IntentNodePayload {
  return {
    kbId: normalizeOptionalText(form.kbId),
    intentCode: form.intentCode.trim(),
    name: form.name.trim(),
    level: form.level,
    parentCode: normalizeOptionalText(form.parentCode),
    description: normalizeOptionalText(form.description),
    examples: form.examplesText
      .split("\n")
      .map((item) => item.trim())
      .filter(Boolean),
    collectionName: normalizeOptionalText(form.collectionName),
    mcpToolId: normalizeOptionalText(form.mcpToolId),
    topK: form.topK.trim() ? Number(form.topK) : null,
    kind: form.kind,
    sortOrder: form.sortOrder.trim() ? Number(form.sortOrder) : 0,
    enabled: form.enabled ? 1 : 0,
    promptSnippet: normalizeOptionalText(form.promptSnippet),
    promptTemplate: normalizeOptionalText(form.promptTemplate),
    paramPromptTemplate: normalizeOptionalText(form.paramPromptTemplate)
  };
}

function normalizeOptionalText(value: string) {
  const normalized = value.trim();
  return normalized || undefined;
}
