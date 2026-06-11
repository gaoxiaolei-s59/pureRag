# 接口文档概览

本文档按当前 Controller 实现整理接口用途、请求方式、路径和主要参数，方便前端联调和后端排查。

## 通用约定

- 后端真实接口路径以 Controller 标注为准，例如 `/knowledge-base`。
- 前端开发环境统一通过代理前缀 `/api` 访问，例如后端 `/knowledge-base` 对应前端请求 `/api/knowledge-base`。
- 认证方式使用 Sa-Token，请求头名称为 `s-token`。除 `/auth/login` 外，其余接口默认需要登录态。
- 普通 JSON 接口统一返回：

```json
{
  "code": "0",
  "message": null,
  "data": {},
  "requestId": null
}
```

- `code = "0"` 表示成功；失败时 `message` 为错误原因。
- 分页接口返回 MyBatis-Plus `IPage` 结构，常用字段包括 `records`、`total`、`size`、`current`、`pages`。
- `/rag/v1/chat` 是 SSE 流式接口，不使用普通 `Result` JSON 返回。

## 认证与用户

### 登录

- 方法：`POST`
- 路径：`/auth/login`
- 说明：用户名密码登录，返回 Sa-Token 登录凭证。
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userName` | string | 是 | 用户名 |
| `password` | string | 是 | 密码 |

- 返回数据：`LoginResponse`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `token` | string | 登录凭证，后续请求放入 `s-token` 请求头 |
| `role` | string | 用户职位/角色 |
| `avatar` | string | 用户头像 |
| `userId` | string | 用户 ID |

### 登录态测试

- 方法：`POST`
- 路径：`/auth/test`
- 说明：测试当前登录态是否可用。
- 入参：无。
- 返回数据：字符串 `ok`。

### 退出登录

- 方法：`POST`
- 路径：`/auth/logout`
- 说明：注销当前 Sa-Token 会话。
- 入参：无。
- 返回数据：无。

### 查询当前用户信息

- 方法：`GET`
- 路径：`/api/user`
- 说明：从 `UserContext` 返回当前登录用户基础信息。
- 入参：无。
- 返回数据：`UserResponse`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userName` | string | 用户名 |
| `avatar` | string | 头像地址 |

## 知识库管理

### 创建知识库

- 方法：`POST`
- 路径：`/knowledge-base`
- 说明：创建知识库，并记录嵌入模型与 Milvus Collection。
- 请求体：`KnowledgeBaseCreateRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 知识库名称 |
| `embeddingModel` | string | 是 | 嵌入模型，如 `qwen3-embedding:8b-fp16` |
| `collectionName` | string | 是 | Milvus Collection 名称 |

- 返回数据：无。

### 修改知识库

- 方法：`PUT`
- 路径：`/knowledge-base/{kb-id}`
- 说明：修改知识库名称。
- 路径参数：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `kb-id` | string | 知识库 ID |

- 请求体：`KnowledgeBaseUpdateRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 否 | 新知识库名称 |

- 返回数据：无。

### 删除知识库

- 方法：`DELETE`
- 路径：`/knowledge-base/{kb-id}`
- 说明：删除指定知识库。
- 路径参数：`kb-id` 知识库 ID。
- 返回数据：无。

### 查询知识库详情

- 方法：`GET`
- 路径：`/knowledge-base/{kb-id}`
- 说明：查询单个知识库详情。
- 路径参数：`kb-id` 知识库 ID。
- 返回数据：`KnowledgeBaseResponse`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 知识库 ID |
| `name` | string | 知识库名称 |
| `embeddingModel` | string | 嵌入模型 |
| `collectionName` | string | Milvus Collection 名称 |
| `documentCount` | number | 文档数量 |
| `createdBy` | string | 创建人 |
| `createTime` | datetime | 创建时间 |
| `updateTime` | datetime | 更新时间 |

### 分页查询知识库

- 方法：`GET`
- 路径：`/knowledge-base`
- 说明：分页查询知识库列表，支持名称模糊查询。
- Query 参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `current` | number | 否 | 当前页 |
| `size` | number | 否 | 每页条数 |
| `name` | string | 否 | 知识库名称关键字 |

- 返回数据：`IPage<KnowledgeBaseResponse>`。

### 查询可用嵌入模型

- 方法：`GET`
- 路径：`/knowledge/models`
- 说明：查询系统可选嵌入模型列表。
- 入参：无。
- 返回数据：`List<string>`。

### 查询全部知识库简要信息

- 方法：`GET`
- 路径：`/knowledge/all`
- 说明：查询所有知识库的 ID 与名称，主要用于意图树创建时选择知识库。
- 入参：无。
- 返回数据：`List<KnowledgeBaseInfoResponse>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 知识库 ID |
| `name` | string | 知识库名称 |

## 文档管理

### 上传文档

- 方法：`POST`
- 路径：`/knowledge-base/{kb-id}/docs/upload`
- Content-Type：`multipart/form-data`
- 说明：上传本地文件或登记远程 URL 文档，创建文档记录。
- 路径参数：`kb-id` 知识库 ID。
- 表单参数：`KnowledgeDocumentUploadRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 否 | 本地文件，`sourceType=file` 时使用 |
| `sourceType` | string | 是 | 来源类型：`file` / `url` |
| `sourceLocation` | string | 否 | 远程 URL，`sourceType=url` 时使用 |
| `scheduleEnabled` | boolean | 否 | 是否开启定时拉取 |
| `scheduleCron` | string | 否 | 定时表达式 |
| `processMode` | string | 是 | 处理模式：`chunk` / `pipeline` |
| `chunkStrategy` | string | 否 | 分块策略：`fixed_size` / `structure_aware` |
| `chunkConfig` | string | 否 | 分块参数 JSON |
| `pipelineId` | string | 否 | 数据通道 ID，`processMode=pipeline` 时使用 |

- 返回数据：`KnowledgeDocumentResponse`。

### 启动文档分块

- 方法：`POST`
- 路径：`/knowledge-base/docs/{doc-id}/chunk`
- 说明：提交文档分块任务，流程包括文本抽取、分块、向量化并写入向量库。
- 路径参数：`doc-id` 文档 ID。
- 返回数据：无。

### 删除文档

- 方法：`DELETE`
- 路径：`/knowledge-base/docs/{doc-id}`
- 说明：删除文档记录，并清理相关分块/向量数据。
- 路径参数：`doc-id` 文档 ID。
- 返回数据：无。

### 查询文档详情

- 方法：`GET`
- 路径：`/knowledge-base/docs/{docId}`
- 说明：查询单个文档配置与处理状态。
- 路径参数：`docId` 文档 ID。
- 返回数据：`KnowledgeDocumentResponse`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 文档 ID |
| `kbId` | string | 知识库 ID |
| `docName` | string | 文档名称 |
| `sourceType` | string | 来源类型 |
| `sourceLocation` | string | 来源位置 |
| `scheduleEnabled` | number | 是否开启定时拉取，0/1 |
| `scheduleCron` | string | 定时表达式 |
| `enabled` | number | 是否启用，0/1 |
| `chunkCount` | number | 分块数量 |
| `fileUrl` | string | 文件访问地址 |
| `fileType` | string | 文件类型 |
| `fileSize` | number | 文件大小，单位字节 |
| `chunkStrategy` | string | 分块策略 |
| `processMode` | string | 处理模式：`chunk` / `pipeline` |
| `chunkConfig` | string | 分块参数 JSON |
| `pipelineId` | string | 数据通道 ID |
| `status` | string | 状态：`pending` / `running` / `failed` / `success` |
| `createdBy` | string | 创建人 |
| `updatedBy` | string | 更新人 |
| `createTime` | datetime | 创建时间 |
| `updateTime` | datetime | 更新时间 |

### 更新文档信息

- 方法：`PUT`
- 路径：`/knowledge-base/docs/{docId}`
- 说明：更新文档名称、启用状态、定时拉取和分块配置。保存后通常需要重新分块才会生效。
- 路径参数：`docId` 文档 ID。
- 请求体：`KnowledgeDocumentUpdateRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `docName` | string | 否 | 文档名称 |
| `enabled` | boolean | 否 | 是否启用 |
| `scheduleEnabled` | boolean | 否 | 是否开启定时拉取 |
| `scheduleCron` | string | 否 | 定时表达式 |
| `chunkStrategy` | string | 否 | 分块策略 |
| `chunkConfig` | string | 否 | 分块参数 JSON |

- 返回数据：无。

### 分页查询文档列表

- 方法：`GET`
- 路径：`/knowledge-base/{kb-id}/docs`
- 说明：分页查询知识库下的文档列表，支持状态和关键字过滤。
- 路径参数：`kb-id` 知识库 ID。
- Query 参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `current` | number | 否 | 当前页 |
| `size` | number | 否 | 每页条数 |
| `keyword` | string | 否 | 文档名称关键字 |
| `status` | string | 否 | 文档状态：`pending` / `running` / `failed` / `success` |

- 返回数据：`IPage<KnowledgeDocumentResponse>`。

## Chunk 管理

### 查询文档 Chunk 列表

- 方法：`GET`
- 路径：`/knowledge-base/docs/{doc-id}/chunks`
- 说明：查询文档下所有分块内容。
- 路径参数：`doc-id` 文档 ID。
- 返回数据：`List<KnowledgeChunkResponse>`。

### 新增 Chunk

- 方法：`POST`
- 路径：`/knowledge-base/docs/{doc-id}/chunks`
- 说明：手动新增文档分块。
- 路径参数：`doc-id` 文档 ID。
- 请求体：`KnowledgeChunkCreateRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | 是 | 分块正文 |
| `index` | number | 否 | 分块下标 |
| `chunkId` | string | 否 | 指定分块 ID |

- 返回数据：`KnowledgeChunkResponse`。

### 更新 Chunk

- 方法：`PUT`
- 路径：`/knowledge-base/docs/{doc-id}/chunks/{chunk-id}`
- 说明：更新分块正文内容。
- 路径参数：`doc-id` 文档 ID，`chunk-id` Chunk ID。
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | 是 | 分块正文 |

- 返回数据：无。

### 删除 Chunk

- 方法：`DELETE`
- 路径：`/knowledge-base/docs/{doc-id}/chunks/{chunk-id}`
- 说明：删除指定分块。
- 路径参数：`doc-id` 文档 ID，`chunk-id` Chunk ID。
- 返回数据：无。

### 启用或禁用单条 Chunk

- 方法：`PATCH`
- 路径：`/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable`
- 说明：启用或禁用单条分块。
- 路径参数：`doc-id` 文档 ID，`chunk-id` Chunk ID。
- Query 参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `value` | boolean | 是 | `true` 启用，`false` 禁用 |

- 返回数据：无。

### 批量启用或禁用 Chunk

- 方法：`PATCH`
- 路径：`/knowledge-base/docs/{doc-id}/chunks/batch-enable`
- 说明：批量启用或禁用分块；请求体不传或 `chunkIds` 为空时，按服务实现可操作文档下所有分块。
- 路径参数：`doc-id` 文档 ID。
- Query 参数：`value`，boolean，`true` 启用，`false` 禁用。
- 请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chunkIds` | string[] | 否 | Chunk ID 列表 |

- 返回数据：无。

## RAG 聊天与记忆

### RAG 流式聊天

- 方法：`GET`
- 路径：`/rag/v1/chat`
- Produces：`text/event-stream;charset=UTF-8`
- 说明：RAG 检索问答主入口，使用 SSE 流式返回模型输出。
- Query 参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userQuestion` | string | 是 | 用户问题 |
| `conversationId` | string | 否 | 会话 ID |
| `deepThinking` | boolean | 否 | 是否开启深度思考，默认 `false` |

- 返回数据：SSE 事件流。

### 停止模型调用

- 方法：`POST`
- 路径：`/rag/v1/stop`
- 说明：按任务 ID 停止正在进行的大模型调用。
- Query 参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `taskId` | string | 是 | 任务 ID |

- 返回数据：无。

### 查询聊天记忆

- 方法：`GET`
- 路径：`/memory/v1/query`
- 说明：查询指定会话的历史消息。
- Query 参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `conversionId` | string | 是 | 会话 ID，当前代码字段名为 `conversionId` |

- 返回数据：`List<MemoryQueryResponse>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `role` | string | 消息角色，如 `user` / `assistant` |
| `content` | string | 消息内容 |

### 查询用户会话列表

- 方法：`GET`
- 路径：`/conversation`
- 说明：查询指定用户的会话列表。
- Query 参数：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | string | 是 | 用户 ID |

- 返回数据：`List<ConversationResponse>`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 会话 ID |
| `userId` | string | 用户 ID |
| `title` | string | 会话标题 |
| `description` | string | 会话描述 |
| `deepThinking` | number | 是否开启深度思考，0/1 |
| `pinned` | number | 是否置顶，0/1 |

## 意图树管理

### 查询意图树

- 方法：`GET`
- 路径：`/intent-tree/query`
- 说明：查询全部意图节点，用于前端构建意图树。
- 入参：无。
- 返回数据：`List<IntentNodeResponse>`。

### 查询单个意图节点

- 方法：`GET`
- 路径：`/intent-tree/{id}`
- 说明：按数据库主键查询单个意图节点详情。
- 路径参数：`id` 意图节点数据库主键。
- 返回数据：`IntentNodeResponse`。

### 创建意图节点

- 方法：`POST`
- 路径：`/intent-tree`
- 说明：创建意图树节点。
- 请求体：`IntentNodeCreateRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `kbId` | string | 否 | 关联知识库 ID |
| `intentCode` | string | 是 | 意图编码 |
| `name` | string | 是 | 意图名称 |
| `level` | number | 是 | 层级：0=DOMAIN，1=CATEGORY，2=TOPIC |
| `parentCode` | string | 否 | 父节点编码 |
| `description` | string | 否 | 描述 |
| `examples` | string[] | 否 | 示例问题 |
| `collectionName` | string | 否 | Milvus Collection 名称 |
| `mcpToolId` | string | 否 | MCP 工具 ID |
| `topK` | number | 否 | 检索 TopK |
| `kind` | number | 是 | 节点类型 |
| `sortOrder` | number | 否 | 排序值 |
| `enabled` | number | 否 | 是否启用，0/1 |
| `promptSnippet` | string | 否 | 短规则片段 |
| `promptTemplate` | string | 否 | 完整 Prompt 模板 |
| `paramPromptTemplate` | string | 否 | MCP 参数提取提示词模板 |

- 返回数据：无。

### 删除意图节点

- 方法：`DELETE`
- 路径：`/intent-tree/{id}`
- 说明：删除指定意图节点。
- 路径参数：`id` 意图节点数据库主键。
- 返回数据：无。

### 更新意图节点

- 方法：`PUT`
- 路径：`/intent-tree/{id}`
- 说明：更新意图节点配置。
- 路径参数：`id` 意图节点数据库主键。
- 请求体：`IntentNodeUpdateRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `kbId` | string | 否 | 关联知识库 ID |
| `intentCode` | string | 是 | 意图编码 |
| `name` | string | 是 | 意图名称 |
| `level` | number | 是 | 层级：0=DOMAIN，1=CATEGORY，2=TOPIC |
| `parentCode` | string | 否 | 父节点编码 |
| `description` | string | 否 | 描述 |
| `examples` | string[] | 否 | 示例问题 |
| `collectionName` | string | 否 | Milvus Collection 名称 |
| `mcpToolId` | string | 否 | MCP 工具 ID |
| `topK` | number | 否 | 检索 TopK |
| `kind` | number | 是 | 节点类型 |
| `sortOrder` | number | 否 | 排序值 |
| `enabled` | number | 是 | 是否启用，0/1 |
| `promptSnippet` | string | 否 | 短规则片段 |
| `promptTemplate` | string | 否 | 完整 Prompt 模板 |
| `paramPromptTemplate` | string | 否 | MCP 参数提取提示词模板 |

- 返回数据：无。

### IntentNodeResponse 字段概览

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recordId` | string | 数据库主键，更新/删除时使用 |
| `id` | string | 业务唯一标识 |
| `kbId` | string | 关联知识库 ID |
| `name` | string | 节点名称 |
| `description` | string | 节点描述 |
| `examples` | string[] | 示例问题 |
| `level` | string | 节点层级枚举 |
| `parentId` | string | 父节点业务 ID |
| `collectionName` | string | Collection 名称 |
| `mcpToolId` | string | MCP 工具 ID |
| `kind` | string | 节点类型枚举 |
| `topK` | number | 检索 TopK |
| `sortOrder` | number | 排序值 |
| `enabled` | number | 是否启用，0/1 |
| `promptSnippet` | string | 短规则片段 |
| `promptTemplate` | string | 完整 Prompt 模板 |
| `paramPromptTemplate` | string | MCP 参数提取提示词模板 |
| `fullPath` | string | 节点完整路径 |
| `children` | string[] | 直接子节点业务 ID 列表 |
