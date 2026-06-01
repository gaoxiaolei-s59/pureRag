# Rag Web Frontend Refactor Design

## 背景

当前 `rag-web` 前端把登录、聊天、知识库、文档、意图树、弹窗、接口调用和工具函数都堆在 `src/App.tsx` 与 `src/api.ts` 中，导致以下问题：

- 单文件职责过多，阅读与修改成本持续上升
- 页面状态互相耦合，新增功能容易牵连其他区域
- 只能通过 `appView` 做本地视图切换，缺少可直达 URL
- 样式集中在单个大文件中，页面结构与视觉层没有边界

## 目标

- 把单页状态切换改为真实路由结构
- 按业务域拆分聊天、知识库、意图树三个模块
- 把 `api.ts` 拆成按领域组织的 service 文件
- 保持现有功能行为基本不变，优先完成结构重构
- 在不引入额外状态库的前提下，顺手优化页面层级和后台风格

## 非目标

- 本次不改后端接口契约
- 本次不引入 Zustand、Redux、React Query 等状态或请求库
- 本次不大改业务流程和字段定义

## 路由设计

- `/login`
  - 登录页，保留用户名密码登录与 token 登录
- `/chat`
  - 对话页，负责会话列表、消息流、SSE 问答
- `/knowledge`
  - 知识库总览页，负责知识库列表、创建、编辑、删除
- `/knowledge/:kbId/docs`
  - 知识库文档页，负责文档上传、详情、分块、chunk 管理
- `/intent-tree`
  - 意图树页，负责树结构浏览、筛选、节点编辑
- `/`
  - 默认重定向到 `/chat` 或 `/login`

## 目录设计

计划把 `rag-web/src` 调整为以下结构：

```text
src/
  app/
    router.tsx
    layouts/
      DashboardLayout.tsx
  components/
    common/
      Modal.tsx
      StatCard.tsx
      EmptyState.tsx
  hooks/
    useAuthGuard.ts
  modules/
    auth/
      pages/LoginPage.tsx
      services/auth.ts
      storage.ts
    chat/
      pages/ChatPage.tsx
      hooks/useChatPage.ts
      services/chat.ts
      types.ts
    knowledge/
      pages/KnowledgePage.tsx
      pages/KnowledgeDocumentsPage.tsx
      services/knowledge.ts
      types.ts
      utils.ts
    intent/
      pages/IntentTreePage.tsx
      services/intent.ts
      types.ts
      utils.ts
  services/
    http.ts
  styles/
    global.css
    layout.css
    components.css
    pages/
      login.css
      chat.css
      knowledge.css
      intent.css
  main.tsx
```

## 状态边界

- 认证状态
  - 由 `auth/storage.ts` 与布局层协作管理
  - 负责 token 持久化、退出登录、路由守卫
- 页面状态
  - 由各自页面组件本地管理
  - 聊天、知识库、文档、意图树互不共享内部表单状态
- 共享 UI
  - 仅抽取通用弹窗、空态、统计卡片和布局壳子
  - 不把业务细节塞回公共组件

## 视觉调整方向

- 保留现有后台产品定位，不改为花哨营销页
- 统一导航、卡片、表格、详情面板、按钮体系
- 登录页保留品牌感，但简化信息噪声
- 业务页统一为左侧导航 + 顶部标题区 + 主工作区结构
- 文档页和意图树页加强信息层级，让操作入口更稳定

## 迁移顺序

1. 先补路由和布局骨架
2. 再拆认证与 service 层
3. 再迁移聊天页
4. 再迁移知识库与文档页
5. 最后迁移意图树页和通用组件
6. 收尾做样式整理与构建验证

## 风险与控制

- 风险：迁移过程中容易漏掉旧页面里的局部状态逻辑
  - 控制：按页面分批迁移，不做跨页面混合改造
- 风险：样式拆分后出现选择器丢失
  - 控制：优先保留现有 class 命名，再逐步重组 CSS 文件
- 风险：路由改造后登录态跳转异常
  - 控制：通过统一的 token 读写与受保护布局兜住
