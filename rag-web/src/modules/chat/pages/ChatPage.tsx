import {
  Bot,
  Brain,
  CheckCircle2,
  Copy,
  FileText,
  Github,
  Lightbulb,
  Loader2,
  LogOut,
  MessageSquare,
  MessageSquareText,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Send,
  Settings,
  ThumbsDown,
  ThumbsUp
} from "lucide-react";
import { FormEvent, KeyboardEvent, useEffect, useMemo } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useAuthGuard } from "../../../hooks/useAuthGuard";
import { clearAuthStorage, getStoredUserName } from "../../auth/storage";
import { logout } from "../../auth/services/auth";
import { useChatPage } from "../hooks/useChatPage";

const CHAT_STARTERS = [
  {
    icon: <FileText size={18} />,
    title: "内容总结",
    description: "提炼 3-5 条关键信息与行动点",
    prompt: "请帮我总结以下内容，并列出关键结论和下一步行动。"
  },
  {
    icon: <CheckCircle2 size={18} />,
    title: "任务拆解",
    description: "把目标拆成可执行步骤与优先级",
    prompt: "请把下面需求拆解为步骤，按优先级输出可执行计划。"
  },
  {
    icon: <Lightbulb size={18} />,
    title: "灵感扩展",
    description: "给出多种方案并比较优缺点",
    prompt: "请围绕这个主题给出多个方案，并比较各自优缺点。"
  }
];

export function ChatPage() {
  const { isAuthenticated } = useAuthGuard();
  const navigate = useNavigate();
  const chat = useChatPage();
  const userName = getStoredUserName();

  useEffect(() => {
    void chat.refreshConversations();
  }, []);

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  async function handleLogout() {
    try {
      await logout();
    } catch {
      // 后端退出失败时仍然清理本地登录态，避免前端停留在错误状态
    }
    clearAuthStorage();
    navigate("/login", { replace: true });
  }

  function handlePromptKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) {
      return;
    }
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await chat.sendMessage();
  }

  const activeConversationTitle = useMemo(
    () => chat.conversations.find((item) => item.id === chat.conversationId)?.title || "新对话",
    [chat.conversationId, chat.conversations]
  );

  const userInitial = userName.slice(0, 1).toUpperCase() || "A";

  return (
    <main className="chat-shell">
      <aside className="chat-sidebar">
        <div className="chat-brand">
          <div className="chat-brand-mark">
            <Bot size={22} />
          </div>
          <div>
            <strong>PureAgent 智能体</strong>
            <span>Powered by AI</span>
          </div>
        </div>

        <section className="quick-card">
          <div className="quick-card-head">
            <span>快速开始</span>
            <b>新内容</b>
          </div>
          <button type="button" className="new-chat-card" onClick={chat.newConversation}>
            <span className="plus-cube">
              <Plus size={20} />
            </span>
            <span>
              <strong>新建对话</strong>
              <small>从空白开始</small>
            </span>
          </button>
          <button type="button" className="mini-pill" onClick={() => navigate("/knowledge")}>
            <Settings size={16} />
            管理后台
          </button>
        </section>

        <section className="search-card">
          <div className="search-card-head">
            <span>搜索对话</span>
            <small>Ctrl / Cmd + K</small>
          </div>
          <label className="soft-search">
            <Search size={18} />
            <input
              value={chat.conversationSearch}
              onChange={(event) => chat.setConversationSearch(event.target.value)}
              placeholder="搜索对话..."
            />
          </label>
        </section>

        {chat.filteredConversations.length ? (
          <section className="conversation-list">
            <div className="conversation-list-head">
              <span>最近对话</span>
              <button type="button" onClick={() => void chat.refreshConversations()}>
                <RefreshCw size={15} />
              </button>
            </div>
            <div className="conversation-items">
              {chat.filteredConversations.map((conversation) => (
                <button
                  type="button"
                  key={conversation.id}
                  className={`conversation-item ${conversation.id === chat.conversationId ? "active" : ""}`}
                  onClick={() => void chat.selectConversation(conversation)}
                >
                  <MessageSquareText size={16} />
                  <span>
                    <strong>{conversation.title || "未命名对话"}</strong>
                    <small>{conversation.description || conversation.id}</small>
                  </span>
                </button>
              ))}
            </div>
          </section>
        ) : (
          <div className="empty-chat-history">
            <MessageSquare size={56} />
            <span>{chat.userId ? "暂无对话记录" : "登录后显示对话记录"}</span>
          </div>
        )}

        <div className="chat-user">
          <span className="user-avatar">{userInitial}</span>
          <div className="chat-user-meta">
            <strong>{userName}</strong>
            <small>{chat.userId ? "已登录" : "访客"}</small>
          </div>
          <button type="button" className="ghost-button small chat-user-action" onClick={() => void handleLogout()}>
            <LogOut size={16} />
          </button>
        </div>
      </aside>

      <section className="chat-main">
        <header className="chat-topbar">
          <strong>{activeConversationTitle}</strong>
          <button type="button" className="star-button">
            <Github size={18} />
            Star
            <span>--</span>
          </button>
        </header>

        <div className="chat-workspace">
          <section className={`conversation-stream ${chat.messages.length === 0 ? "empty" : ""}`}>
            {chat.messages.length === 0 ? (
              <div className="chat-landing">
                <div className="chat-landing-badge">
                  <Bot size={16} />
                  <span>PureAgent 智能问答</span>
                </div>
                <h1>
                  把问题变成
                  <span>清晰答案</span>
                </h1>
                <p>结构化提问、知识检索与深度思考，一次对话给出可执行方案</p>
              </div>
            ) : (
              chat.messages.map((message, index) => (
                <article className={`chat-turn ${message.role}`} key={message.id}>
                  {message.role === "user" ? (
                    <>
                      <div className="user-bubble">{message.content}</div>
                      <div className="turn-actions user-actions">
                        <button type="button" aria-label="复制">
                          <Copy size={16} />
                        </button>
                        <button type="button" aria-label="编辑">
                          <Pencil size={16} />
                        </button>
                      </div>
                    </>
                  ) : (
                    <>
                      <div className="assistant-body">
                        {message.thinkingContent ? (
                          <div className="assistant-thinking-panel">
                            <button
                              type="button"
                              className={`assistant-thinking-toggle ${message.thinkingCollapsed ? "collapsed" : ""}`}
                              onClick={() => chat.toggleThinking(message.id)}
                            >
                              <span className="assistant-thinking-summary">
                                <Brain size={16} />
                                <span>
                                  {message.thinkingDurationSeconds
                                    ? `已思考（用时 ${message.thinkingDurationSeconds} 秒）`
                                    : "深度思考中..."}
                                </span>
                              </span>
                            </button>
                            {!message.thinkingCollapsed ? (
                              <div className="assistant-thinking">{message.thinkingContent}</div>
                            ) : null}
                          </div>
                        ) : null}
                        <div className="assistant-text">
                          {message.content ||
                            (chat.chatLoading && index === chat.messages.length - 1
                              ? message.thinkingContent
                                ? "正在整理最终回答..."
                                : "正在思考..."
                              : "")}
                        </div>
                      </div>
                      <div className="turn-actions">
                        <button type="button" aria-label="复制">
                          <Copy size={16} />
                        </button>
                        <button type="button" aria-label="重新生成">
                          <RotateCcw size={16} />
                        </button>
                        <button type="button" aria-label="赞同">
                          <ThumbsUp size={16} />
                        </button>
                        <button type="button" aria-label="不赞同">
                          <ThumbsDown size={16} />
                        </button>
                      </div>
                    </>
                  )}
                </article>
              ))
            )}
          </section>

          <form className="chat-composer" onSubmit={handleSubmit}>
            <textarea
              value={chat.question}
              onChange={(event) => chat.setQuestion(event.target.value)}
              onKeyDown={handlePromptKeyDown}
              placeholder="输入你的问题..."
              rows={4}
            />
            <div className="composer-actions">
              <label className="thinking-pill">
                <input
                  type="checkbox"
                  checked={chat.deepThinking}
                  onChange={(event) => chat.setDeepThinking(event.target.checked)}
                />
                <Brain size={16} />
                深度思考
              </label>
              <div className="composer-actions-right">
                {chat.chatLoading ? (
                  <button type="button" className="ghost-button small" onClick={() => void chat.stopChat()}>
                    停止生成
                  </button>
                ) : null}
                <button type="submit" className="send-button" aria-label="发送" disabled={chat.chatLoading}>
                  {chat.chatLoading ? <Loader2 className="spin" size={18} /> : <Send size={18} />}
                </button>
              </div>
            </div>
          </form>

          <div className="composer-hint">
            <kbd>Enter</kbd> 发送 · <kbd>Shift + Enter</kbd> 换行
          </div>

          {chat.messages.length === 0 ? (
            <section className="chat-launchpad">
              <div className="chat-launchpad-head">
                <span>试试这些开场</span>
              </div>
              <div className="chat-launchpad-grid">
                {CHAT_STARTERS.map((starter) => (
                  <button
                    type="button"
                    key={starter.title}
                    className="chat-launch-card"
                    onClick={() => chat.setQuestion(starter.prompt)}
                  >
                    <div className="chat-launch-card-icon">{starter.icon}</div>
                    <div className="chat-launch-card-copy">
                      <strong>{starter.title}</strong>
                      <p>{starter.description}</p>
                      <small>推荐问法：{starter.prompt}</small>
                    </div>
                  </button>
                ))}
              </div>
            </section>
          ) : null}
        </div>
      </section>
    </main>
  );
}
