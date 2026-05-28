# RagTest

`RagTest` 是一个围绕 RAG 问答、知识库管理、意图识别、MCP 工具接入和流式对话构建的多模块实验项目。

当前仓库主要包含以下模块：

- `rag-bootstrap`
  - 主业务模块，包含问答链路、知识库、记忆、意图识别和 API 接口
- `infra`
  - 大模型、Embedding、Rerank 等基础能力封装
- `framework`
  - 通用约定、异常、Web 返回体和基础设施封装
- `rag-mcp`
  - MCP 服务示例与工具接入
- `rag-web`
  - 前端页面与交互演示

## 检索架构

当前项目的检索主流程已经整理成专门文档，详细说明了：

- SSE 问答入口
- 记忆加载与压缩
- 问题改写
- 意图识别
- `SYSTEM-only` 短路逻辑
- 默认知识库检索链路
- Prompt 组装与流式输出

详细文档见：

- [RAG 检索全流程架构文档](./docs/rag-retrieval-architecture.md)

## 当前检索主流程图

```mermaid
flowchart TD
    A["前端 / SSE 客户端"] --> B["RagChatController<br/>GET /rag/v1/chat"]
    B --> C["RagChatServiceImpl.streamChat"]
    C --> D["创建 StreamChatContext<br/>注册 StreamTaskManager<br/>绑定 SseEmitter 回调"]
    D --> E["ChatPipeLine.execute"]

    E --> F["loadMemory(context)"]
    F --> F1["MemoryServiceImpl<br/>压缩历史(按需)"]
    F1 --> F2["加载摘要消息 + 历史消息"]
    F2 --> G["Rewrite(context, memory)"]

    G --> G1["MutiQueryRewriteService<br/>结合最近4轮对话改写问题"]
    G1 --> G2["输出 rewrittenQuestion / subQuestions"]
    G2 --> H["resolveIntents(context)"]

    H --> H1["IntentResolver<br/>对子问题或改写后问题做意图识别"]
    H1 --> H2["DefaultIntentClassifier<br/>命中 SYSTEM / KB / MCP 等意图"]

    H2 --> I{"是否全部为 SYSTEM 意图?"}
    I -->|是| J["handleSystemOnly"]
    J --> J1["跳过 embedding"]
    J1 --> J2["跳过知识库检索"]
    J2 --> J3["直接组装历史消息 + 当前问题"]
    J3 --> N["llmService.streamChat"]

    I -->|否| K["进入 RAG 检索链路"]
    K --> K1["确定 retrievalQuestion<br/>优先 rewrittenQuestion"]
    K1 --> K2["embeddingService.embed(retrievalQuestion)"]
    K2 --> K3["RagRetrievalService.searchSimilarChunks"]
    K3 --> K4["MilvusRagRetrievalService<br/>查询默认 collection + topK"]
    K4 --> K5["返回 RetrievedChunk 列表"]

    K5 --> L["buildChatRequest"]
    L --> L1["加载 rag-system-prompt.txt"]
    L1 --> L2["把 chunks 拼进 {{context}}"]
    L2 --> L3["拼接摘要消息 + 历史消息 + 当前用户问题"]
    L3 --> N["llmService.streamChat"]

    N --> O["流式回调"]
    O --> O1["onThinking -> SSE 推送思考过程"]
    O --> O2["onContent -> SSE 推送正文"]
    O --> O3["onComplete -> 保存用户消息和助手回复到记忆"]

    O3 --> P["MemoryServiceImpl.saveConversation"]
```

当前前端UI
![img.png](img.png)
## 当前实现边界

当前仓库已经实现了“记忆 + 改写 + 意图识别 + 默认知识库检索 + 流式输出”的完整闭环，但下面两块还没有完全接入主流程：

- 还没有按命中的意图节点动态路由到具体 `kbId / collectionName`
- `MCP` 意图识别已经具备，但工具调用链路还没有进入主问答编排

如果你准备继续演进这条链路，建议先阅读：

- [docs/rag-retrieval-architecture.md](/Users/gaoxaiolei/IdeaProjects/RagTest/docs/rag-retrieval-architecture.md)
