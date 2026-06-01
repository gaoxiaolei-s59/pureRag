import { describe, expect, it } from "vitest";
import { buildIntentNodePayload, createEditIntentForm } from "./form";
import type { IntentNode } from "./types";

describe("intent form", () => {
  it("createEditIntentForm should map node detail into editable state", () => {
    const node: IntentNode = {
      recordId: "node-1",
      id: "faq.shipping",
      kbId: "kb-1",
      name: "发货问题",
      description: "处理发货时效咨询",
      examples: ["多久发货", "什么时候发出"],
      level: "CATEGORY",
      parentId: "faq",
      collectionName: "shipping_docs",
      mcpToolId: "tool-1",
      kind: "KB",
      topK: 5,
      sortOrder: 3,
      enabled: 1,
      promptSnippet: "snippet",
      promptTemplate: "template",
      paramPromptTemplate: "param-template"
    };

    const form = createEditIntentForm(node);

    expect(form.intentCode).toBe("faq.shipping");
    expect(form.examplesText).toBe("多久发货\n什么时候发出");
    expect(form.topK).toBe("5");
    expect(form.enabled).toBe(true);
  });

  it("buildIntentNodePayload should trim text and normalize optional fields", () => {
    const payload = buildIntentNodePayload({
      kbId: "",
      intentCode: " faq.shipping ",
      name: " 发货问题 ",
      level: 1,
      parentCode: "",
      description: " 处理发货时效咨询 ",
      examplesText: "多久发货\n \n什么时候发出 ",
      collectionName: " shipping_docs ",
      mcpToolId: " ",
      topK: " 5 ",
      kind: 0,
      sortOrder: " 3 ",
      enabled: true,
      promptSnippet: " snippet ",
      promptTemplate: " template ",
      paramPromptTemplate: " "
    });

    expect(payload).toEqual({
      kbId: undefined,
      intentCode: "faq.shipping",
      name: "发货问题",
      level: 1,
      parentCode: undefined,
      description: "处理发货时效咨询",
      examples: ["多久发货", "什么时候发出"],
      collectionName: "shipping_docs",
      mcpToolId: undefined,
      topK: 5,
      kind: 0,
      sortOrder: 3,
      enabled: 1,
      promptSnippet: "snippet",
      promptTemplate: "template",
      paramPromptTemplate: undefined
    });
  });
});
