import {
  BarChart3,
  Bot,
  Brain,
  CheckCircle2,
  ChevronDown,
  ClipboardList,
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
  Plus,
  RefreshCw,
  Search,
  Send,
  Settings,
  Sparkles,
  Trash2,
  UploadCloud,
  UserRound
} from "lucide-react";
import { FormEvent, ReactNode, useEffect, useMemo, useRef, useState } from "react";
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
  const [userName, setUserName] = useState("admin");
  const [password, setPassword] = useState("123456");
  const [notice, setNotice] = useState("准备就绪");
  const [loading, setLoading] = useState(false);

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
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: "system", content: "选择知识库并完成分块后，可以在这里发起 RAG 问答。" }
  ]);
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
    setStoredToken(token);
    setNotice(token ? "s-token 已保存" : "s-token 已清空");
    if (!token) {
      setBases([]);
      setSelectedKbId("");
      setDocuments([]);
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

  function stopChat() {
    abortRef.current?.abort();
    setChatLoading(false);
    setNotice("已停止当前聊天请求");
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
              <strong>Ragent AI 智能体</strong>
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

          <div className="hero-area">
            <div className="hero-badge">
              <Bot size={16} />
              RAG 智能问答
            </div>
            <h1>
              把问题变成<span>清晰答案</span>
            </h1>
            <p>结构化提问、知识检索与深度思考，一次对话给出可执行方案</p>

            <form className="hero-prompt" onSubmit={handleChat}>
              <textarea
                value={question}
                onChange={(event) => setQuestion(event.target.value)}
                placeholder="输入你的问题..."
                rows={3}
              />
              <div className="hero-prompt-actions">
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

            <div className="shortcut-hint">
              <kbd>Enter</kbd> 发送 · <kbd>Shift + Enter</kbd> 换行
            </div>

            <div className="prompt-examples">
              <span>试试这些开场</span>
            </div>

            <div className="example-grid">
              <button type="button" className="example-card" onClick={() => setQuestion("请帮我总结以下内容，并列出行动点")}>
                <span className="example-icon">
                  <BookIcon size={20} />
                </span>
                <strong>内容总结</strong>
                <small>提炼 3-5 条关键信息与行动点</small>
              </button>
              <button type="button" className="example-card" onClick={() => setQuestion("请把下面目标拆成可执行步骤与优先级")}>
                <span className="example-icon">
                  <CheckCircle2 size={20} />
                </span>
                <strong>任务拆解</strong>
                <small>把目标拆成可执行步骤与优先级</small>
              </button>
              <button type="button" className="example-card" onClick={() => setQuestion("请围绕这个主题给出多个方案并比较优缺点")}>
                <span className="example-icon">
                  <Lightbulb size={20} />
                </span>
                <strong>灵感扩展</strong>
                <small>给出多个方案并比较优缺点</small>
              </button>
            </div>

            {messages.length > 0 && (
              <section className="floating-chat-result">
                {messages.slice(-4).map((message, index) => (
                  <div className={`message ${message.role}`} key={`${message.role}-${index}`}>
                    <div className="avatar">{message.role === "assistant" ? <Bot size={16} /> : message.role === "user" ? "我" : "i"}</div>
                    <p>{message.content || (chatLoading ? "生成中..." : "")}</p>
                  </div>
                ))}
                {chatLoading && (
                  <button type="button" className="ghost-button small" onClick={stopChat}>
                    停止生成
                  </button>
                )}
              </section>
            )}
          </div>
        </section>
      </main>
    );
  }

  return (
    <main className="admin-shell">
      <aside className="admin-sidebar">
        <div className="admin-brand">
          <div className="admin-brand-mark">R</div>
          <div>
            <strong>Ragent AI 管理后台</strong>
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
            </div>
          </div>

          <section className="stats-grid">
            <StatCard icon={<Database size={24} />} label="知识库" value={bases.length} />
            <StatCard icon={<FileText size={24} />} label="文档数" value={documents.length} />
            <StatCard icon={<FolderOpen size={24} />} label="含文档知识库" value={bases.filter((item) => (item.documentCount ?? 0) > 0).length} />
            <StatCard icon={<Layers3 size={24} />} label="完成文档" value={successDocs} />
          </section>

          <section className="admin-card create-strip">
            <form className="knowledge-create-form" onSubmit={handleCreateKb}>
              <input value={newKbName} onChange={(event) => setNewKbName(event.target.value)} placeholder="知识库名称" />
              <input
                value={newKbCollection}
                onChange={(event) => setNewKbCollection(event.target.value)}
                placeholder="Milvus Collection"
              />
              <input value={newKbModel} onChange={(event) => setNewKbModel(event.target.value)} placeholder="嵌入模型" />
              <button type="submit" className="gradient-button">
                <Plus size={18} />
                新建知识库
              </button>
            </form>
          </section>

          <section className="admin-card knowledge-layout">
            <div className="knowledge-list">
              <div className="section-head">
                <strong>知识库列表</strong>
                <span>{filteredBases.length} 个</span>
              </div>
              {filteredBases.length > 0 ? (
                filteredBases.map((kb) => (
                  <button
                    type="button"
                    className={`knowledge-row ${kb.id === selectedKbId ? "active" : ""}`}
                    key={kb.id}
                    onClick={() => setSelectedKbId(kb.id)}
                  >
                    <span className="row-icon">
                      <Database size={18} />
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

            <div className="document-area">
              <div className="section-head">
                <strong>{selectedKb ? `${selectedKb.name} 的文档` : "文档"}</strong>
                <button type="button" className="outline-button small" onClick={() => void refreshDocuments()}>
                  <RefreshCw size={15} />
                  刷新
                </button>
              </div>

              <form className="upload-dock" onSubmit={handleUpload}>
                <div className="segmented">
                  <button type="button" className={sourceType === "file" ? "active" : ""} onClick={() => setSourceType("file")}>
                    文件
                  </button>
                  <button type="button" className={sourceType === "url" ? "active" : ""} onClick={() => setSourceType("url")}>
                    URL
                  </button>
                </div>
                {sourceType === "file" ? (
                  <input type="file" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
                ) : (
                  <input value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} placeholder="文档 URL" />
                )}
                <div className="upload-options">
                  <select value={chunkStrategy} onChange={(event) => setChunkStrategy(event.target.value)}>
                    <option value="fixed_size">fixed_size</option>
                    <option value="structure_aware">structure_aware</option>
                  </select>
                  <label className="toggle">
                    <input checked={scheduleEnabled} onChange={(event) => setScheduleEnabled(event.target.checked)} type="checkbox" />
                    定时拉取
                  </label>
                  <button type="submit" className="gradient-button">
                    <UploadCloud size={17} />
                    上传
                  </button>
                </div>
                <textarea value={chunkConfig} onChange={(event) => setChunkConfig(event.target.value)} rows={3} />
              </form>

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
          </section>

          <section className="admin-card login-card">
            <div>
              <strong>登录与 Token</strong>
              <span>{notice}</span>
            </div>
            <form onSubmit={handleLogin}>
              <input value={userName} onChange={(event) => setUserName(event.target.value)} placeholder="用户名" />
              <input value={password} onChange={(event) => setPassword(event.target.value)} placeholder="密码" type="password" />
              <button type="submit" className="outline-button" disabled={loading}>
                {loading ? <Loader2 className="spin" size={16} /> : <LogIn size={16} />}
                登录
              </button>
            </form>
            <div className="token-row">
              <input value={token} onChange={(event) => setToken(event.target.value)} placeholder="s-token" />
              <button type="button" className="outline-button" onClick={handleSaveToken}>
                保存
              </button>
            </div>
          </section>
        </div>
      </section>
    </main>
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
