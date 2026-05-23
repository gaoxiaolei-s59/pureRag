import {
  ArrowLeft,
  BarChart3,
  Bot,
  Brain,
  CheckCircle2,
  ChevronDown,
  ClipboardList,
  Copy,
  Database,
  FileText,
  FolderOpen,
  Github,
  Home,
  Layers3,
  Lightbulb,
  Loader2,
  LogIn,
  MessageSquare,
  MessageSquareText,
  PanelLeftClose,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Send,
  Settings,
  Sparkles,
  ThumbsDown,
  ThumbsUp,
  Trash2,
  UploadCloud,
  UserRound,
  X
} from "lucide-react";
import { FormEvent, KeyboardEvent, ReactNode, useEffect, useMemo, useRef, useState } from "react";
import {
  createKnowledgeBase,
  deleteKnowledgeDocument,
  fetchKnowledgeBases,
  fetchKnowledgeDocuments,
  getStoredToken,
  KnowledgeBase,
  KnowledgeDocument,
  login,
  setStoredToken,
  startDocumentChunk,
  streamChat,
  uploadKnowledgeDocument
} from "./api";

type ChatMessage = {
  role: "user" | "assistant" | "system";
  content: string;
};

type ViewMode = "chat" | "knowledge";

const DEFAULT_CHUNK_CONFIG = JSON.stringify({ chunkSize: 512, overlapSize: 128 }, null, 2);

function formatBytes(value?: number) {
  if (!value) {
    return "-";
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function statusText(status?: string) {
  const map: Record<string, string> = {
    pending: "待处理",
    running: "处理中",
    failed: "失败",
    success: "完成"
  };
  return status ? map[status] ?? status : "-";
}

export function App() {
  const [viewMode, setViewMode] = useState<ViewMode>("chat");
  const [token, setToken] = useState(getStoredToken());
  const [tokenDraft, setTokenDraft] = useState(getStoredToken());
  const [userName, setUserName] = useState("admin");
  const [password, setPassword] = useState("123456");
  const [notice, setNotice] = useState("准备就绪");
  const [loading, setLoading] = useState(false);
  const [createKbOpen, setCreateKbOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [kbDetailOpen, setKbDetailOpen] = useState(false);

  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState("");
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [kbSearch, setKbSearch] = useState("");

  const [newKbName, setNewKbName] = useState("");
  const [newKbCollection, setNewKbCollection] = useState("");
  const [newKbModel, setNewKbModel] = useState("qwen-emb-8b");

  const [sourceType, setSourceType] = useState<"file" | "url">("file");
  const [sourceUrl, setSourceUrl] = useState("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf");
  const [file, setFile] = useState<File | null>(null);
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduleCron, setScheduleCron] = useState("0 0/30 * * * ?");
  const [chunkStrategy, setChunkStrategy] = useState("fixed_size");
  const [chunkConfig, setChunkConfig] = useState(DEFAULT_CHUNK_CONFIG);

  const [conversationId, setConversationId] = useState("001");
  const [question, setQuestion] = useState("");
  const [deepThinking, setDeepThinking] = useState(false);
  const [chatLoading, setChatLoading] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const abortRef = useRef<AbortController | null>(null);

  const selectedKb = useMemo(
    () => bases.find((item) => item.id === selectedKbId),
    [bases, selectedKbId]
  );

  const filteredBases = useMemo(() => {
    const keyword = kbSearch.trim().toLowerCase();
    if (!keyword) {
      return bases;
    }
    return bases.filter((item) =>
      `${item.name} ${item.collectionName} ${item.embeddingModel}`.toLowerCase().includes(keyword)
    );
  }, [bases, kbSearch]);

  const successDocs = documents.filter((item) => item.status === "success").length;

  async function refreshBases() {
    if (!getStoredToken()) {
      setNotice("请先登录或填写 s-token");
      return;
    }
    setLoading(true);
    try {
      const page = await fetchKnowledgeBases();
      setBases(page.records ?? []);
      if (!selectedKbId && page.records?.length) {
        setSelectedKbId(page.records[0].id);
      }
      setNotice("知识库列表已刷新");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新知识库失败");
    } finally {
      setLoading(false);
    }
  }

  async function refreshDocuments(kbId = selectedKbId) {
    if (!kbId) {
      setDocuments([]);
      return;
    }
    setLoading(true);
    try {
      const page = await fetchKnowledgeDocuments(kbId);
      setDocuments(page.records ?? []);
      setNotice("文档列表已刷新");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新文档失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (token) {
      void refreshBases();
      return;
    }
    setNotice("请先登录或填写 s-token");
  }, [token]);

  useEffect(() => {
    void refreshDocuments(selectedKbId);
  }, [selectedKbId]);

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      const response = await login(userName, password);
      setToken(response.token);
      setTokenDraft(response.token);
      setStoredToken(response.token);
      setNotice("登录成功，s-token 已保存");
      await refreshBases();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "登录失败");
    } finally {
      setLoading(false);
    }
  }

  function handleSaveToken() {
    const nextToken = tokenDraft.trim();
    setToken(nextToken);
    setStoredToken(nextToken);
    setNotice(nextToken ? "s-token 已保存" : "s-token 已清空");
    if (!nextToken) {
      setBases([]);
      setSelectedKbId("");
      setDocuments([]);
      setKbDetailOpen(false);
    }
  }

  async function handleCreateKb(event: FormEvent) {
    event.preventDefault();
    if (!newKbName || !newKbCollection || !newKbModel) {
      setNotice("请填写知识库名称、Collection 和模型");
      return;
    }
    setLoading(true);
    try {
      await createKnowledgeBase({
        name: newKbName,
        collectionName: newKbCollection,
        embeddingModel: newKbModel
      });
      setNewKbName("");
      setNewKbCollection("");
      setNotice("知识库创建成功");
      setCreateKbOpen(false);
      await refreshBases();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "创建知识库失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleUpload(event: FormEvent) {
    event.preventDefault();
    if (!selectedKbId) {
      setNotice("请先选择知识库");
      return;
    }
    if (sourceType === "file" && !file) {
      setNotice("请选择要上传的文件");
      return;
    }
    if (sourceType === "url" && !sourceUrl) {
      setNotice("请输入文档 URL");
      return;
    }
    setLoading(true);
    try {
      await uploadKnowledgeDocument({
        kbId: selectedKbId,
        sourceType,
        file,
        sourceLocation: sourceType === "url" ? sourceUrl : undefined,
        scheduleEnabled,
        scheduleCron: scheduleEnabled ? scheduleCron : undefined,
        processMode: "chunk",
        chunkStrategy,
        chunkConfig
      });
      setNotice("文档已上传");
      setFile(null);
      setUploadOpen(false);
      await refreshDocuments();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "上传失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleChunk(docId: string) {
    setLoading(true);
    try {
      await startDocumentChunk(docId);
      setNotice("分块任务已提交");
      await refreshDocuments();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "提交分块失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleDeleteDoc(docId: string) {
    setLoading(true);
    try {
      await deleteKnowledgeDocument(docId);
      setNotice("文档已删除");
      await refreshDocuments();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除文档失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleChat(event: FormEvent) {
    event.preventDefault();
    const userQuestion = question.trim();
    if (!userQuestion || chatLoading) {
      return;
    }

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setQuestion("");
    setChatLoading(true);
    setMessages((current) => [
      ...current,
      { role: "user", content: userQuestion },
      { role: "assistant", content: "" }
    ]);

    try {
      await streamChat(
        { userQuestion, conversationId, deepThinking },
        {
          onToken: (text) => {
            setMessages((current) => {
              const next = [...current];
              const last = next[next.length - 1];
              next[next.length - 1] = { ...last, content: `${last.content}${text}` };
              return next;
            });
          },
          onError: (message) => {
            setNotice(message);
          },
          onDone: () => {
            setChatLoading(false);
          }
        },
        controller.signal
      );
    } catch (error) {
      if (!controller.signal.aborted) {
        setNotice(error instanceof Error ? error.message : "聊天请求失败");
      }
    } finally {
      setChatLoading(false);
    }
  }

  function handlePromptKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) {
      return;
    }
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  }

  function stopChat() {
    abortRef.current?.abort();
    setChatLoading(false);
    setNotice("已停止当前聊天请求");
  }

  if (!token) {
    return (
      <main className="login-shell">
        <section className="login-hero">
          <div className="login-brand">
            <span className="login-brand-mark">
              <Bot size={28} />
            </span>
            <div>
              <strong>PureAgent</strong>
              <small>Knowledge Console</small>
            </div>
          </div>
          <div className="login-copy">
            <span>RAG 智能知识工作台</span>
            <h1>把知识库、文档分块和智能问答放进一个清爽入口</h1>
            <p>登录后进入 PureAgent，可以管理知识库、上传文档、触发分块，并直接发起 RAG 问答。</p>
          </div>
          <div className="login-feature-strip">
            <span>知识库管理</span>
            <span>文档向量化</span>
            <span>流式问答</span>
          </div>
        </section>

        <section className="login-panel">
          <div>
            <span className="login-eyebrow">Welcome back</span>
            <h2>登录 PureAgent</h2>
            <p>使用账号密码登录，或者粘贴已有 s-token 直接进入。</p>
          </div>

          <form className="login-form" onSubmit={handleLogin}>
            <label>
              <span>用户名</span>
              <input value={userName} onChange={(event) => setUserName(event.target.value)} placeholder="admin" />
            </label>
            <label>
              <span>密码</span>
              <input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="请输入密码" type="password" />
            </label>
            <button type="submit" className="gradient-button" disabled={loading}>
              {loading ? <Loader2 className="spin" size={17} /> : <LogIn size={17} />}
              登录并进入
            </button>
          </form>

          <div className="login-divider">或</div>

          <div className="login-token-box">
            <textarea value={tokenDraft} onChange={(event) => setTokenDraft(event.target.value)} placeholder="粘贴 s-token" rows={3} />
            <button type="button" className="outline-button" onClick={handleSaveToken}>
              保存 Token
            </button>
          </div>

          <p className="login-notice">{notice}</p>
        </section>
      </main>
    );
  }

  if (viewMode === "chat") {
    return (
      <main className="chat-shell">
        <aside className="chat-sidebar">
          <div className="chat-brand">
            <div className="chat-brand-mark">
              <Bot size={24} />
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
            <button type="button" className="new-chat-card" onClick={() => setMessages([])}>
              <span className="plus-cube">
                <Plus size={22} />
              </span>
              <span>
                <strong>新建对话</strong>
                <small>从空白开始</small>
              </span>
            </button>
            <button type="button" className="mini-pill" onClick={() => setViewMode("knowledge")}>
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
              <input placeholder="搜索对话..." />
            </label>
          </section>

          <div className="empty-chat-history">
            <MessageSquare size={54} />
            <span>暂无对话记录</span>
          </div>

          <div className="chat-user">
            <span className="user-avatar">A</span>
            <strong>{userName}</strong>
            <span>•••</span>
          </div>
        </aside>

        <section className="chat-main">
          <header className="chat-topbar">
            <strong>新对话</strong>
            <button className="star-button" type="button">
              <Github size={18} />
              Star
              <span>--</span>
            </button>
          </header>

          <div className="chat-workspace">
            <section className={`conversation-stream ${messages.length === 0 ? "empty" : ""}`}>
              {messages.length === 0 ? (
                <div className="chat-empty-state">
                  <div className="chat-empty-mark">
                    <Bot size={26} />
                  </div>
                  <h1>今天想研究什么？</h1>
                  <p>选择知识库并完成分块后，可以直接在这里发起 RAG 问答。</p>
                  <div className="starter-row">
                    <button type="button" onClick={() => setQuestion("请总结当前知识库里的核心业务规则")}>
                      <FileText size={16} />
                      总结业务规则
                    </button>
                    <button type="button" onClick={() => setQuestion("请把商品上下架规则整理成流程")}>
                      <CheckCircle2 size={16} />
                      整理流程
                    </button>
                    <button type="button" onClick={() => setQuestion("请基于知识库生成一组测试问题")}>
                      <Lightbulb size={16} />
                      生成问题
                    </button>
                  </div>
                </div>
              ) : (
                messages.map((message, index) => (
                  <article className={`chat-turn ${message.role}`} key={`${message.role}-${index}`}>
                    {message.role === "user" ? (
                      <>
                        <div className="user-bubble">{message.content}</div>
                        <div className="turn-actions user-actions">
                          <button type="button" aria-label="复制">
                            <Copy size={17} />
                          </button>
                          <button type="button" aria-label="编辑">
                            <Pencil size={17} />
                          </button>
                        </div>
                      </>
                    ) : (
                      <>
                        <div className="assistant-text">
                          {message.content || (chatLoading && index === messages.length - 1 ? "正在思考..." : "")}
                        </div>
                        <div className="turn-actions">
                          <button type="button" aria-label="复制">
                            <Copy size={17} />
                          </button>
                          <button type="button" aria-label="重新生成">
                            <RotateCcw size={17} />
                          </button>
                          <button type="button" aria-label="赞同">
                            <ThumbsUp size={17} />
                          </button>
                          <button type="button" aria-label="不赞同">
                            <ThumbsDown size={17} />
                          </button>
                        </div>
                      </>
                    )}
                  </article>
                ))
              )}
              {chatLoading && (
                <button type="button" className="ghost-button stop-floating" onClick={stopChat}>
                  停止生成
                </button>
              )}
            </section>

            <form className="chat-composer" onSubmit={handleChat}>
              <textarea
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                onKeyDown={handlePromptKeyDown}
                placeholder="输入你的问题..."
                rows={3}
              />
              <div className="composer-actions">
                <label className="thinking-pill">
                  <input
                    type="checkbox"
                    checked={deepThinking}
                    onChange={(event) => setDeepThinking(event.target.checked)}
                  />
                  <Brain size={16} />
                  深度思考
                </label>
                <button type="submit" aria-label="发送" disabled={chatLoading}>
                  {chatLoading ? <Loader2 className="spin" size={18} /> : <Send size={18} />}
                </button>
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

  return (
    <main className="admin-shell">
      <aside className="admin-sidebar">
        <div className="admin-brand">
          <div className="admin-brand-mark">P</div>
          <div>
            <strong>PureAgent 管理后台</strong>
            <span>Knowledge Console</span>
          </div>
        </div>

        <nav className="admin-nav">
          <span className="nav-section">导航</span>
          <button type="button" className="nav-item" onClick={() => setViewMode("chat")}>
            <Home size={19} />
            新对话
          </button>
          <button type="button" className="nav-item">
            <BarChart3 size={19} />
            Dashboard
          </button>
          <button type="button" className="nav-item active">
            <Database size={19} />
            知识库管理
          </button>
          <button type="button" className="nav-item">
            <Layers3 size={19} />
            意图管理
            <ChevronDown size={16} />
          </button>
          <button type="button" className="nav-item sub">
            <ClipboardList size={18} />
            意图列表
          </button>
          <button type="button" className="nav-item">
            <UploadCloud size={19} />
            数据通道
            <ChevronDown size={16} />
          </button>
          <span className="nav-section">设置</span>
          <button type="button" className="nav-item">
            <UserRound size={19} />
            用户管理
          </button>
          <button type="button" className="nav-item">
            <Settings size={19} />
            系统设置
          </button>
        </nav>

        <button type="button" className="collapse-button">
          <PanelLeftClose size={16} />
          收起侧边栏
        </button>
      </aside>

      <section className="admin-main">
        <header className="admin-topbar">
          <label className="admin-search">
            <Search size={19} />
            <input value={kbSearch} onChange={(event) => setKbSearch(event.target.value)} placeholder="筛选知识库..." />
            <kbd>Ctrl K</kbd>
          </label>
          <div className="admin-top-actions">
            <button type="button" className="outline-button" onClick={() => setViewMode("chat")}>
              <MessageSquare size={18} />
              返回聊天
            </button>
            <button type="button" className="outline-button">
              <Github size={18} />
              Star
              <span>--</span>
            </button>
            <div className="admin-user">
              <span>A</span>
              {userName}
              <ChevronDown size={16} />
            </div>
          </div>
        </header>

        <div className="admin-content">
          <div className="breadcrumb">首页 / 知识库管理</div>
          <div className="page-heading">
            <div>
              <h1>知识库管理</h1>
              <p>管理所有知识库及其文档</p>
            </div>
            <div className="page-actions">
              <button type="button" className="outline-button" onClick={() => void refreshBases()}>
                <RefreshCw size={18} />
                刷新
              </button>
              <button type="button" className="gradient-button" onClick={() => setCreateKbOpen(true)}>
                <Plus size={18} />
                新建知识库
              </button>
            </div>
          </div>

          <section className="stats-grid">
            <StatCard icon={<Database size={24} />} label="知识库" value={bases.length} />
            <StatCard icon={<FileText size={24} />} label="文档数" value={documents.length} />
            <StatCard icon={<FolderOpen size={24} />} label="含文档知识库" value={bases.filter((item) => (item.documentCount ?? 0) > 0).length} />
            <StatCard icon={<Layers3 size={24} />} label="完成文档" value={successDocs} />
          </section>

          {!kbDetailOpen ? (
            <section className="admin-card knowledge-overview">
              <div className="section-head">
                <strong>知识库列表</strong>
                <span>{filteredBases.length} 个</span>
              </div>
              <div className="knowledge-card-grid">
                {filteredBases.length > 0 ? (
                  filteredBases.map((kb) => (
                    <button
                      type="button"
                      className="knowledge-card"
                      key={kb.id}
                      onClick={() => {
                        setSelectedKbId(kb.id);
                        setKbDetailOpen(true);
                      }}
                    >
                      <span className="row-icon">
                        <Database size={20} />
                      </span>
                      <span>
                        <strong>{kb.name}</strong>
                        <small>{kb.collectionName}</small>
                      </span>
                      <b>{kb.documentCount ?? 0}</b>
                    </button>
                  ))
                ) : (
                  <div className="empty-panel">暂无知识库，点击上方按钮创建</div>
                )}
              </div>
            </section>
          ) : (
            <section className="admin-card knowledge-detail">
              <div className="document-panel">
                <div className="document-panel-head">
                  <div>
                    <span className="panel-eyebrow">当前知识库</span>
                    <strong>{selectedKb ? `${selectedKb.name} 的文档` : "请选择知识库"}</strong>
                    <small>{selectedKb?.collectionName ?? "选择左侧知识库后查看和上传文档"}</small>
                  </div>
                  <div className="section-actions">
                    <button type="button" className="outline-button small" onClick={() => setKbDetailOpen(false)}>
                      <ArrowLeft size={15} />
                      返回列表
                    </button>
                    <button type="button" className="outline-button small" onClick={() => void refreshDocuments()}>
                      <RefreshCw size={15} />
                      刷新
                    </button>
                    <button type="button" className="gradient-button small" disabled={!selectedKbId} onClick={() => setUploadOpen(true)}>
                      <UploadCloud size={15} />
                      上传文档
                    </button>
                  </div>
                </div>

                <div className="doc-table-wrap">
                  <div className="doc-table">
                    <div className="doc-row doc-head">
                      <span>名称</span>
                      <span>状态</span>
                      <span>大小</span>
                      <span>Chunk</span>
                      <span>操作</span>
                    </div>
                    {documents.map((doc) => (
                      <div className="doc-row" key={doc.id}>
                        <span className="doc-title">{doc.docName}</span>
                        <span>
                          <mark className={`status status-${doc.status ?? "unknown"}`}>{statusText(doc.status)}</mark>
                        </span>
                        <span>{formatBytes(doc.fileSize)}</span>
                        <span>{doc.chunkCount ?? 0}</span>
                        <span className="row-actions">
                          <button type="button" onClick={() => void handleChunk(doc.id)}>
                            分块
                          </button>
                          <button type="button" className="danger-text" onClick={() => void handleDeleteDoc(doc.id)}>
                            删除
                          </button>
                        </span>
                      </div>
                    ))}
                    {!documents.length && <div className="empty-panel">暂无文档，上传后可进行分块</div>}
                  </div>
                </div>
              </div>
            </section>
          )}

          <p className="admin-inline-notice">{notice}</p>
        </div>
      </section>

      {createKbOpen && (
        <Modal title="创建知识库" description="创建一个新的知识库，用于存储和检索文档" onClose={() => setCreateKbOpen(false)}>
          <form className="modal-form" onSubmit={handleCreateKb}>
            <label>
              <span>知识库名称</span>
              <input value={newKbName} onChange={(event) => setNewKbName(event.target.value)} placeholder="例如：产品文档库" />
              <small>为知识库起一个易于识别的名称</small>
            </label>
            <label>
              <span>Embedding 模型</span>
              <select value={newKbModel} onChange={(event) => setNewKbModel(event.target.value)}>
                <option value="qwen-emb-8b">qwen-emb-8b</option>
                <option value="qwen3-local">qwen3-local</option>
                <option value="text-embedding">text-embedding</option>
              </select>
              <small>选择用于向量化文档的模型</small>
            </label>
            <label>
              <span>Collection 名称</span>
              <input
                value={newKbCollection}
                onChange={(event) => setNewKbCollection(event.target.value)}
                placeholder="例如：productdocs"
              />
              <small>只能包含英文、数字和下划线，并以英文或下划线开头</small>
            </label>
            <div className="modal-actions">
              <button type="button" className="outline-button" onClick={() => setCreateKbOpen(false)}>
                取消
              </button>
              <button type="submit" className="gradient-button" disabled={loading}>
                {loading ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
                创建
              </button>
            </div>
          </form>
        </Modal>
      )}

      {uploadOpen && (
        <Modal
          title="上传文档"
          description={selectedKb ? `上传到「${selectedKb.name}」并按策略分块` : "请选择知识库后上传文档"}
          onClose={() => setUploadOpen(false)}
        >
          <form className="modal-form" onSubmit={handleUpload}>
            <div className="segmented modal-segmented">
              <button type="button" className={sourceType === "file" ? "active" : ""} onClick={() => setSourceType("file")}>
                文件
              </button>
              <button type="button" className={sourceType === "url" ? "active" : ""} onClick={() => setSourceType("url")}>
                URL
              </button>
            </div>
            <label>
              <span>{sourceType === "file" ? "选择文件" : "文档 URL"}</span>
              {sourceType === "file" ? (
                <input type="file" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
              ) : (
                <input value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} placeholder="https://example.com/file.pdf" />
              )}
              <small>{sourceType === "file" ? "直接上传本地文件，后端会写入 RustFS" : "后端会先拉取 URL 内容，再上传到 RustFS"}</small>
            </label>
            <label>
              <span>分块策略</span>
              <select value={chunkStrategy} onChange={(event) => setChunkStrategy(event.target.value)}>
                <option value="fixed_size">fixed_size</option>
                <option value="structure_aware">structure_aware</option>
              </select>
            </label>
            <label>
              <span>分块参数 JSON</span>
              <textarea value={chunkConfig} onChange={(event) => setChunkConfig(event.target.value)} rows={4} />
            </label>
            <label className="toggle modal-toggle">
              <input checked={scheduleEnabled} onChange={(event) => setScheduleEnabled(event.target.checked)} type="checkbox" />
              定时拉取 URL 文档
            </label>
            {scheduleEnabled && (
              <label>
                <span>定时表达式</span>
                <input value={scheduleCron} onChange={(event) => setScheduleCron(event.target.value)} placeholder="0 0/30 * * * ?" />
              </label>
            )}
            <div className="modal-actions">
              <button type="button" className="outline-button" onClick={() => setUploadOpen(false)}>
                取消
              </button>
              <button type="submit" className="gradient-button" disabled={loading || !selectedKbId}>
                {loading ? <Loader2 className="spin" size={16} /> : <UploadCloud size={16} />}
                上传
              </button>
            </div>
          </form>
        </Modal>
      )}
    </main>
  );
}

function Modal({
  title,
  description,
  children,
  onClose
}: {
  title: string;
  description: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="modal-backdrop" role="presentation">
      <section className="modal-card" role="dialog" aria-modal="true" aria-label={title}>
        <button type="button" className="modal-close" onClick={onClose} aria-label="关闭">
          <X size={22} />
        </button>
        <div className="modal-head">
          <h2>{title}</h2>
          <p>{description}</p>
        </div>
        {children}
      </section>
    </div>
  );
}

function StatCard({ icon, label, value }: { icon: ReactNode; label: string; value: number }) {
  return (
    <div className="stat-card">
      <span>{icon}</span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
      </div>
      <em>全部</em>
    </div>
  );
}

function BookIcon({ size = 20 }: { size?: number }) {
  return <FileText size={size} />;
}
