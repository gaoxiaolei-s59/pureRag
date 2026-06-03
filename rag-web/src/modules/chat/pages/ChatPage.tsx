import {
  Atom,
  Bot,
  Brain,
  Copy,
  Database,
  Loader2,
  LogOut,
  MessageSquare,
  MessageSquareText,
  Moon,
  PanelLeft,
  Paperclip,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Send,
  Settings,
  Sun,
  ThumbsDown,
  ThumbsUp,
  UploadCloud
} from "lucide-react";
import { FormEvent, KeyboardEvent, useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useAppTheme } from "../../../app/theme";
import { useAuthGuard } from "../../../hooks/useAuthGuard";
import { clearAuthStorage, getStoredUserName } from "../../auth/storage";
import { logout } from "../../auth/services/auth";
import { useChatPage } from "../hooks/useChatPage";

export function ChatPage() {
  const { isAuthenticated } = useAuthGuard();
  const navigate = useNavigate();
  const chat = useChatPage();
  const userName = getStoredUserName();
  const { theme, isDarkTheme, toggleTheme } = useAppTheme();
  const [smartSearch, setSmartSearch] = useState(false);

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

  const userInitial = userName.slice(0, 1).toUpperCase() || "A";

  return (
    <main className="chat-shell" data-theme={theme}>
      <aside className="chat-sidebar">
        <div className="chat-brand">
          <div className="chat-brand-mark">
            <Bot size={24} />
          </div>
          <strong>PureAgent</strong>
          <div className="chat-sidebar-tools">
            <button type="button" aria-label="搜索对话">
              <Search size={20} />
            </button>
            <button type="button" aria-label="折叠侧边栏">
              <PanelLeft size={20} />
            </button>
          </div>
        </div>

        <section className="search-card">
          <button type="button" className="new-chat-card" onClick={chat.newConversation}>
            <Plus size={20} />
            开启新对话
          </button>
          <label className="soft-search">
            <Search size={18} />
            <input
              value={chat.conversationSearch}
              onChange={(event) => chat.setConversationSearch(event.target.value)}
              placeholder="搜索对话..."
            />
          </label>
          <button type="button" className="mini-pill" onClick={() => navigate("/knowledge")}>
            <Settings size={16} />
            管理后台
          </button>
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
          <span />
          <div className="chat-topbar-actions">
            <button type="button" className="upload-button">
              <UploadCloud size={20} />
              拖拽至此上传
            </button>
            <button
              type="button"
              className="theme-toggle-button"
              aria-label={isDarkTheme ? "切换浅色模式" : "切换深色模式"}
              title={isDarkTheme ? "切换浅色模式" : "切换深色模式"}
              onClick={toggleTheme}
            >
              {isDarkTheme ? <Sun size={18} /> : <Moon size={18} />}
            </button>
          </div>
        </header>

        <div className="chat-workspace">
          <section className={`conversation-stream ${chat.messages.length === 0 ? "empty" : ""}`}>
            {chat.messages.length === 0 ? (
              <div className="chat-landing">
                <div className="chat-landing-mark">
                  <Bot size={34} />
                </div>
                <h1>PureAgent 知识问答</h1>
                <p>连接知识库、意图识别与深度检索，把项目资料变成可追问的答案。</p>
                <div className="chat-landing-meta" aria-label="项目能力">
                  <span>
                    <Database size={15} />
                    知识库增强
                  </span>
                  <span>
                    <Brain size={15} />
                    深度思考
                  </span>
                </div>
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
              placeholder="给 PureAgent 发送消息"
              rows={4}
            />
            <div className="composer-actions">
              <button
                type="button"
                className={`thinking-pill ${chat.deepThinking ? "active" : "inactive"}`}
                aria-pressed={chat.deepThinking}
                onClick={() => chat.setDeepThinking(!chat.deepThinking)}
              >
                <Brain size={16} />
                深度思考
              </button>
              <button
                type="button"
                className={`thinking-pill ${smartSearch ? "active" : "inactive"}`}
                aria-pressed={smartSearch}
                onClick={() => setSmartSearch((current) => !current)}
              >
                <Atom size={16} />
                智能搜索
              </button>
              <div className="composer-actions-right">
                {chat.chatLoading ? (
                  <button type="button" className="ghost-button small" onClick={() => void chat.stopChat()}>
                    停止生成
                  </button>
                ) : null}
                <button type="button" className="icon-tool-button" aria-label="添加附件">
                  <Paperclip size={20} />
                </button>
                <button type="submit" className="send-button" aria-label="发送" disabled={chat.chatLoading}>
                  {chat.chatLoading ? <Loader2 className="spin" size={18} /> : <Send size={18} />}
                </button>
              </div>
            </div>
          </form>

          <div className="composer-hint">
            <kbd>Enter</kbd> 发送 · <kbd>Shift + Enter</kbd> 换行
          </div>
        </div>
      </section>
    </main>
  );
}
