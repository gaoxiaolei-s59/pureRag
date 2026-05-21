import {
  Bot,
  CheckCircle2,
  Database,
  FileText,
  Loader2,
  LogIn,
  MessageSquareText,
  Plus,
  RefreshCw,
  Send,
  Settings2,
  Sparkles,
  Trash2,
  UploadCloud
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
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
  const [token, setToken] = useState(getStoredToken());
  const [userName, setUserName] = useState("admin");
  const [password, setPassword] = useState("123456");
  const [notice, setNotice] = useState("准备就绪");
  const [loading, setLoading] = useState(false);

  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState("");
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);

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

  async function refreshBases() {
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
    void refreshBases();
  }, []);

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

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark">
            <Sparkles size={22} />
          </div>
          <div>
            <strong>RagTest</strong>
            <span>Knowledge Console</span>
          </div>
        </div>

        <form className="panel compact-form" onSubmit={handleLogin}>
          <div className="panel-title">
            <LogIn size={16} />
            <span>登录</span>
          </div>
          <input value={userName} onChange={(event) => setUserName(event.target.value)} placeholder="用户名" />
          <input
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="密码"
            type="password"
          />
          <button type="submit" className="primary-button" disabled={loading}>
            {loading ? <Loader2 className="spin" size={16} /> : <LogIn size={16} />}
            登录并保存
          </button>
        </form>

        <section className="panel compact-form">
          <div className="panel-title">
            <Settings2 size={16} />
            <span>s-token</span>
          </div>
          <textarea
            value={token}
            onChange={(event) => setToken(event.target.value)}
            rows={3}
            placeholder="也可以手动粘贴 s-token"
          />
          <button type="button" className="ghost-button" onClick={handleSaveToken}>
            <CheckCircle2 size={16} />
            保存 Token
          </button>
        </section>

        <section className="panel">
          <div className="panel-title space-between">
            <span className="inline-title">
              <Database size={16} />
              知识库
            </span>
            <button type="button" className="icon-button" onClick={() => void refreshBases()} aria-label="刷新知识库">
              <RefreshCw size={16} />
            </button>
          </div>
          <div className="kb-list">
            {bases.map((kb) => (
              <button
                type="button"
                key={kb.id}
                className={`kb-item ${kb.id === selectedKbId ? "active" : ""}`}
                onClick={() => setSelectedKbId(kb.id)}
              >
                <strong>{kb.name}</strong>
                <span>{kb.collectionName}</span>
              </button>
            ))}
            {!bases.length && <p className="empty-text">暂无知识库</p>}
          </div>
        </section>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <h1>知识库工作台</h1>
            <p>{selectedKb ? `${selectedKb.name} / ${selectedKb.collectionName}` : "选择或创建一个知识库开始"}</p>
          </div>
          <div className="notice">{notice}</div>
        </header>

        <div className="grid">
          <section className="panel create-panel">
            <div className="panel-title">
              <Plus size={16} />
              <span>新建知识库</span>
            </div>
            <form className="inline-form" onSubmit={handleCreateKb}>
              <input value={newKbName} onChange={(event) => setNewKbName(event.target.value)} placeholder="名称" />
              <input
                value={newKbCollection}
                onChange={(event) => setNewKbCollection(event.target.value)}
                placeholder="Milvus Collection"
              />
              <input value={newKbModel} onChange={(event) => setNewKbModel(event.target.value)} placeholder="嵌入模型" />
              <button type="submit" className="primary-button">
                <Plus size={16} />
                创建
              </button>
            </form>
          </section>

          <section className="panel upload-panel">
            <div className="panel-title">
              <UploadCloud size={16} />
              <span>上传文档</span>
            </div>
            <form className="upload-form" onSubmit={handleUpload}>
              <div className="segmented">
                <button
                  type="button"
                  className={sourceType === "file" ? "active" : ""}
                  onClick={() => setSourceType("file")}
                >
                  文件
                </button>
                <button
                  type="button"
                  className={sourceType === "url" ? "active" : ""}
                  onClick={() => setSourceType("url")}
                >
                  URL
                </button>
              </div>
              {sourceType === "file" ? (
                <input type="file" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
              ) : (
                <input value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} placeholder="文档 URL" />
              )}
              <div className="two-columns">
                <select value={chunkStrategy} onChange={(event) => setChunkStrategy(event.target.value)}>
                  <option value="fixed_size">fixed_size</option>
                  <option value="structure_aware">structure_aware</option>
                </select>
                <label className="toggle">
                  <input
                    type="checkbox"
                    checked={scheduleEnabled}
                    onChange={(event) => setScheduleEnabled(event.target.checked)}
                  />
                  定时拉取
                </label>
              </div>
              {scheduleEnabled && (
                <input value={scheduleCron} onChange={(event) => setScheduleCron(event.target.value)} placeholder="cron" />
              )}
              <textarea value={chunkConfig} onChange={(event) => setChunkConfig(event.target.value)} rows={4} />
              <button type="submit" className="primary-button">
                <UploadCloud size={16} />
                上传
              </button>
            </form>
          </section>

          <section className="panel docs-panel">
            <div className="panel-title space-between">
              <span className="inline-title">
                <FileText size={16} />
                文档
              </span>
              <button type="button" className="ghost-button small" onClick={() => void refreshDocuments()}>
                <RefreshCw size={15} />
                刷新
              </button>
            </div>
            <div className="table">
              <div className="table-row table-head">
                <span>名称</span>
                <span>状态</span>
                <span>大小</span>
                <span>Chunk</span>
                <span>操作</span>
              </div>
              {documents.map((doc) => (
                <div className="table-row" key={doc.id}>
                  <span className="doc-name">{doc.docName}</span>
                  <span>
                    <mark className={`status status-${doc.status ?? "unknown"}`}>{statusText(doc.status)}</mark>
                  </span>
                  <span>{formatBytes(doc.fileSize)}</span>
                  <span>{doc.chunkCount ?? 0}</span>
                  <span className="actions">
                    <button type="button" className="icon-button" onClick={() => void handleChunk(doc.id)} aria-label="分块">
                      <RefreshCw size={15} />
                    </button>
                    <button type="button" className="icon-button danger" onClick={() => void handleDeleteDoc(doc.id)} aria-label="删除">
                      <Trash2 size={15} />
                    </button>
                  </span>
                </div>
              ))}
              {!documents.length && <p className="empty-text">这个知识库还没有文档</p>}
            </div>
          </section>

          <section className="panel chat-panel">
            <div className="panel-title">
              <MessageSquareText size={16} />
              <span>RAG 问答</span>
            </div>
            <div className="chat-body">
              {messages.map((message, index) => (
                <div className={`message ${message.role}`} key={`${message.role}-${index}`}>
                  <div className="avatar">{message.role === "assistant" ? <Bot size={16} /> : message.role === "user" ? "我" : "i"}</div>
                  <p>{message.content || (chatLoading && index === messages.length - 1 ? "生成中..." : "")}</p>
                </div>
              ))}
            </div>
            <form className="chat-form" onSubmit={handleChat}>
              <div className="chat-options">
                <input
                  value={conversationId}
                  onChange={(event) => setConversationId(event.target.value)}
                  placeholder="conversationId"
                />
                <label className="toggle">
                  <input
                    type="checkbox"
                    checked={deepThinking}
                    onChange={(event) => setDeepThinking(event.target.checked)}
                  />
                  深度思考
                </label>
              </div>
              <div className="prompt-row">
                <textarea
                  value={question}
                  onChange={(event) => setQuestion(event.target.value)}
                  rows={3}
                  placeholder="输入问题，例如：商品的上下架规则"
                />
                <button type="submit" className="primary-button send-button" disabled={chatLoading}>
                  {chatLoading ? <Loader2 className="spin" size={18} /> : <Send size={18} />}
                </button>
                {chatLoading && (
                  <button type="button" className="ghost-button stop-button" onClick={stopChat}>
                    停止
                  </button>
                )}
              </div>
            </form>
          </section>
        </div>
      </section>
    </main>
  );
}
