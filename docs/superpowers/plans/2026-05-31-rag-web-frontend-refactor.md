# Rag Web Frontend Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `rag-web` 从单文件前端改造成真实路由、多模块服务层和可维护的页面结构，同时保留现有主要功能。

**Architecture:** 使用 `react-router-dom` 建立真实路由，按 `auth/chat/knowledge/intent` 拆分页面与 service，保留 React 本地状态，不引入额外全局状态库。样式分为全局、布局、组件和页面四层，减少 `App.tsx` 与 `styles.css` 的巨型职责。

**Tech Stack:** React 19、TypeScript、Vite、react-router-dom、Fetch API、CSS

---

### Task 1: 建立路由骨架与基础依赖

**Files:**
- Modify: `rag-web/package.json`
- Modify: `rag-web/src/main.tsx`
- Create: `rag-web/src/app/router.tsx`
- Create: `rag-web/src/app/layouts/DashboardLayout.tsx`

- [ ] **Step 1: 增加路由依赖**

```json
{
  "dependencies": {
    "react-router-dom": "^7.x"
  }
}
```

- [ ] **Step 2: 运行安装命令更新锁文件**

Run: `npm install`
Expected: 新增 `react-router-dom`，`package-lock.json` 更新成功

- [ ] **Step 3: 在入口文件挂载路由**

```tsx
import { RouterProvider } from "react-router-dom";
import { router } from "./app/router";
import "./styles/global.css";

<RouterProvider router={router} />
```

- [ ] **Step 4: 创建路由与布局壳子**

```tsx
export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    path: "/",
    element: <DashboardLayout />,
    children: [...]
  }
]);
```

- [ ] **Step 5: 运行构建验证路由骨架**

Run: `npm run build`
Expected: 构建成功，至少不存在路由相关编译错误

### Task 2: 拆分认证模块与 HTTP 基础层

**Files:**
- Create: `rag-web/src/services/http.ts`
- Create: `rag-web/src/modules/auth/storage.ts`
- Create: `rag-web/src/modules/auth/services/auth.ts`
- Create: `rag-web/src/modules/auth/pages/LoginPage.tsx`
- Create: `rag-web/src/hooks/useAuthGuard.ts`

- [ ] **Step 1: 先写最小认证存储与跳转测试思路**

```ts
// 以 token 为空时跳转 login、token 存在时允许访问为行为基线
```

- [ ] **Step 2: 实现通用请求函数**

```ts
export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  // 统一拼接 /api、注入 s-token、处理 JSON 响应
}
```

- [ ] **Step 3: 把 token 读写收口到 storage**

```ts
export function getStoredToken() {}
export function setStoredToken(token: string) {}
export function clearStoredToken() {}
```

- [ ] **Step 4: 实现登录页与路由守卫**

```tsx
// 登录成功后 navigate("/chat")
// 无 token 时受保护页面重定向到 /login
```

- [ ] **Step 5: 运行构建验证认证链路**

Run: `npm run build`
Expected: 登录页、存储与路由守卫编译通过

### Task 3: 拆分聊天模块

**Files:**
- Create: `rag-web/src/modules/chat/types.ts`
- Create: `rag-web/src/modules/chat/services/chat.ts`
- Create: `rag-web/src/modules/chat/hooks/useChatPage.ts`
- Create: `rag-web/src/modules/chat/pages/ChatPage.tsx`

- [ ] **Step 1: 把聊天类型与服务从旧文件抽离**

```ts
export type Conversation = {...}
export function fetchConversations(...) {}
export function streamChat(...) {}
```

- [ ] **Step 2: 抽出页面状态 hook**

```ts
export function useChatPage() {
  // 会话列表、消息列表、SSE 状态、发送与中断逻辑
}
```

- [ ] **Step 3: 用新页面组件承接聊天 UI**

```tsx
export function ChatPage() {
  const state = useChatPage();
  return (...)
}
```

- [ ] **Step 4: 运行构建验证聊天页面迁移**

Run: `npm run build`
Expected: 聊天页编译通过，旧 `App.tsx` 不再承担聊天职责

### Task 4: 拆分知识库与文档模块

**Files:**
- Create: `rag-web/src/modules/knowledge/types.ts`
- Create: `rag-web/src/modules/knowledge/services/knowledge.ts`
- Create: `rag-web/src/modules/knowledge/utils.ts`
- Create: `rag-web/src/modules/knowledge/pages/KnowledgePage.tsx`
- Create: `rag-web/src/modules/knowledge/pages/KnowledgeDocumentsPage.tsx`

- [ ] **Step 1: 抽离知识库、文档、chunk 类型与 API**

```ts
export type KnowledgeBase = {...}
export function fetchKnowledgeBases() {}
export function uploadKnowledgeDocument() {}
```

- [ ] **Step 2: 拆总览页与文档页**

```tsx
// KnowledgePage 只负责知识库列表与元信息
// KnowledgeDocumentsPage 只负责某个 kb 下的文档与 chunk
```

- [ ] **Step 3: 用路由参数承接 selectedKbId**

```tsx
const { kbId } = useParams();
```

- [ ] **Step 4: 运行构建验证知识库模块**

Run: `npm run build`
Expected: `/knowledge` 与 `/knowledge/:kbId/docs` 编译通过

### Task 5: 拆分意图树模块与公共组件

**Files:**
- Create: `rag-web/src/modules/intent/types.ts`
- Create: `rag-web/src/modules/intent/services/intent.ts`
- Create: `rag-web/src/modules/intent/utils.ts`
- Create: `rag-web/src/modules/intent/pages/IntentTreePage.tsx`
- Create: `rag-web/src/components/common/Modal.tsx`
- Create: `rag-web/src/components/common/StatCard.tsx`
- Create: `rag-web/src/components/common/EmptyState.tsx`

- [ ] **Step 1: 抽离意图树类型、树构建和映射工具**

```ts
export function buildIntentTree(nodes: IntentNode[]) {}
export function intentKindText(kind?: string) {}
```

- [ ] **Step 2: 抽离意图树 API**

```ts
export function fetchIntentNodes() {}
export function createIntentNode() {}
export function updateIntentNode() {}
```

- [ ] **Step 3: 提炼公共组件减少重复结构**

```tsx
export function Modal(...) {}
export function StatCard(...) {}
```

- [ ] **Step 4: 运行构建验证意图页与公共组件**

Run: `npm run build`
Expected: 意图树页编译通过，公共组件引用正常

### Task 6: 清理旧入口并拆分样式

**Files:**
- Delete: `rag-web/src/App.tsx`
- Delete: `rag-web/src/api.ts`
- Rename/Replace: `rag-web/src/styles.css` -> `rag-web/src/styles/global.css`
- Create: `rag-web/src/styles/layout.css`
- Create: `rag-web/src/styles/components.css`
- Create: `rag-web/src/styles/pages/login.css`
- Create: `rag-web/src/styles/pages/chat.css`
- Create: `rag-web/src/styles/pages/knowledge.css`
- Create: `rag-web/src/styles/pages/intent.css`

- [ ] **Step 1: 拆样式文件并按页面导入**

```css
/* global 负责变量和 reset */
/* layout 负责侧边栏和主区域 */
/* pages/* 负责单页面样式 */
```

- [ ] **Step 2: 删除旧巨型入口文件**

```text
移除 App.tsx 与 api.ts 的唯一职责，确保不再被引用
```

- [ ] **Step 3: 运行完整构建验证**

Run: `npm run build`
Expected: 构建成功，无缺失引用

- [ ] **Step 4: 检查最终变更状态**

Run: `git status --short`
Expected: 仅出现本次重构相关新增、修改、删除文件
