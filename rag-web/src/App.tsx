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
  LogOut,
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
  Conversation,
  createKnowledgeBase,
  createKnowledgeChunk,
  deleteKnowledgeBase,
  deleteKnowledgeChunk,
  deleteKnowledgeDocument,
  fetchConversationMessages,
  fetchConversations,
  fetchIntentNodeDetail,
  fetchIntentNodes,
  fetchKnowledgeBaseDetail,
  fetchKnowledgeBases,
  fetchKnowledgeChunks,
  fetchKnowledgeDocumentDetail,
  fetchKnowledgeDocuments,
  getStoredToken,
  IntentNode,
  KnowledgeBase,
  KnowledgeChunk,
  KnowledgeDocument,
  login,
  logout,
  setStoredToken,
  startDocumentChunk,
  stopChatTask,
  streamChat,
  updateKnowledgeBase,
  updateKnowledgeChunk,
  updateKnowledgeDocument,
  uploadKnowledgeDocument
} from "./api";

type ChatMessage = {
  id: string;
  role: "user" | "assistant" | "system";
  content: string;
  thinkingContent?: string;
  thinkingCollapsed?: boolean;
  thinkingStartedAt?: number;
  thinkingDurationSeconds?: number;
};

type AppView = "login" | "chat" | "knowledge" | "intent";
type IntentTreeNode = IntentNode & { treeChildren: IntentTreeNode[] };

const DEFAULT_CHUNK_CONFIG = JSON.stringify({ chunkSize: 512, overlapSize: 128 }, null, 2);
const USER_ID_KEY = "rag-web:user-id";

function createConversationId() {
  return crypto.randomUUID().replaceAll("-", "");
}

function createMessageId() {
  return crypto.randomUUID();
}

function toChatHistoryMessage(role: string, content: string): ChatMessage {
  return {
    id: createMessageId(),
    role: role === "assistant" || role === "system" ? role : "user",
    content
  };
}

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

function intentLevelText(level?: string) {
  const map: Record<string, string> = {
    DOMAIN: "领域",
    CATEGORY: "分类",
    TOPIC: "主题"
  };
  return level ? map[level] ?? level : "-";
}

function intentKindText(kind?: string) {
  const map: Record<string, string> = {
    KB: "知识库",
    MCP: "MCP 工具",
    SYSTEM: "系统能力"
  };
  return kind ? map[kind] ?? kind : "-";
}

function buildIntentTree(nodes: IntentNode[]) {
  const nodeMap = new Map<string, IntentTreeNode>();
  for (const node of nodes) {
    nodeMap.set(node.id, { ...node, treeChildren: [] });
  }
  const roots: IntentTreeNode[] = [];
  for (const node of nodeMap.values()) {
    const parentId = node.parentId ?? "";
    if (parentId && nodeMap.has(parentId)) {
      nodeMap.get(parentId)?.treeChildren.push(node);
    } else {
      roots.push(node);
    }
  }
  const sortNodes = (items: IntentTreeNode[]) => {
    items.sort((left, right) => {
      const orderGap = (left.sortOrder ?? 0) - (right.sortOrder ?? 0);
      if (orderGap !== 0) {
        return orderGap;
      }
      return left.name.localeCompare(right.name, "zh-CN");
    });
    items.forEach((item) => sortNodes(item.treeChildren));
  };
  sortNodes(roots);
  return roots;
}

export function App() {
  const [token, setToken] = useState(getStoredToken());
  const [appView, setAppView] = useState<AppView>(token ? "chat" : "login");
  const [tokenDraft, setTokenDraft] = useState(getStoredToken());
  const [userId, setUserId] = useState(localStorage.getItem(USER_ID_KEY) ?? "");
  const [userName, setUserName] = useState("admin");
  const [password, setPassword] = useState("123456");
  const [notice, setNotice] = useState("准备就绪");
  const [loading, setLoading] = useState(false);
  const [createKbOpen, setCreateKbOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [kbDetailOpen, setKbDetailOpen] = useState(false);

  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [selectedKbId, setSelectedKbId] = useState("");
  const [selectedKbDetail, setSelectedKbDetail] = useState<KnowledgeBase | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [selectedDocDetail, setSelectedDocDetail] = useState<KnowledgeDocument | null>(null);
  const [docChunks, setDocChunks] = useState<KnowledgeChunk[]>([]);
  const [kbSearch, setKbSearch] = useState("");
  const [editKbOpen, setEditKbOpen] = useState(false);
  const [docDetailOpen, setDocDetailOpen] = useState(false);

  const [newKbName, setNewKbName] = useState("");
  const [newKbCollection, setNewKbCollection] = useState("");
  const [newKbModel, setNewKbModel] = useState("qwen-emb-8b");
  const [editKbName, setEditKbName] = useState("");

  const [sourceType, setSourceType] = useState<"file" | "url">("file");
  const [sourceUrl, setSourceUrl] = useState("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf");
  const [file, setFile] = useState<File | null>(null);
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [scheduleCron, setScheduleCron] = useState("0 0/30 * * * ?");
  const [chunkStrategy, setChunkStrategy] = useState("fixed_size");
  const [chunkConfig, setChunkConfig] = useState(DEFAULT_CHUNK_CONFIG);
  const [docFormName, setDocFormName] = useState("");
  const [docFormEnabled, setDocFormEnabled] = useState(true);
  const [docFormScheduleEnabled, setDocFormScheduleEnabled] = useState(false);
  const [docFormScheduleCron, setDocFormScheduleCron] = useState("0 0/30 * * * ?");
  const [docFormChunkStrategy, setDocFormChunkStrategy] = useState("fixed_size");
  const [docFormChunkConfig, setDocFormChunkConfig] = useState(DEFAULT_CHUNK_CONFIG);
  const [newChunkContent, setNewChunkContent] = useState("");
  const [intentNodes, setIntentNodes] = useState<IntentNode[]>([]);
  const [intentLoading, setIntentLoading] = useState(false);
  const [intentSearch, setIntentSearch] = useState("");
  const [selectedIntentId, setSelectedIntentId] = useState("");
  const [selectedIntentDetail, setSelectedIntentDetail] = useState<IntentNode | null>(null);

  const [conversationId, setConversationId] = useState(createConversationId());
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [conversationSearch, setConversationSearch] = useState("");
  const [question, setQuestion] = useState("");
  const [deepThinking, setDeepThinking] = useState(false);
  const [chatLoading, setChatLoading] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const abortRef = useRef<AbortController | null>(null);
  const taskIdRef = useRef("");

  function finalizeAssistantThinking(messageId: string) {
    setMessages((current) =>
      current.map((message) => {
        if (message.id !== messageId || !message.thinkingContent) {
          return message;
        }
        return {
          ...message,
          thinkingCollapsed: true,
          thinkingDurationSeconds: message.thinkingStartedAt
            ? Math.max(1, Math.round((Date.now() - message.thinkingStartedAt) / 1000))
            : message.thinkingDurationSeconds
        };
      })
    );
  }

  function toggleThinking(messageId: string) {
    setMessages((current) =>
      current.map((message) =>
        message.id === messageId
          ? {
              ...message,
              thinkingCollapsed: !message.thinkingCollapsed
            }
          : message
      )
    );
  }

  async function stopActiveChat(showStoppedNotice = true) {
    const taskId = taskIdRef.current;
    let stopFailed = false;
    if (taskId) {
      try {
        await stopChatTask(taskId);
      } catch (error) {
        stopFailed = true;
        setNotice(error instanceof Error ? error.message : "停止聊天失败");
      }
    }
    abortRef.current?.abort();
    taskIdRef.current = "";
    setChatLoading(false);
    if (showStoppedNotice && !stopFailed) {
      setNotice("已停止当前聊天请求");
    }
  }

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
  const enabledIntentCount = intentNodes.filter((item) => item.enabled === 1).length;

  const filteredConversations = useMemo(() => {
    const keyword = conversationSearch.trim().toLowerCase();
    if (!keyword) {
      return conversations;
    }
    return conversations.filter((item) =>
      `${item.title ?? ""} ${item.description ?? ""}`.toLowerCase().includes(keyword)
    );
  }, [conversationSearch, conversations]);

  const filteredIntentNodes = useMemo(() => {
    const keyword = intentSearch.trim().toLowerCase();
    if (!keyword) {
      return intentNodes;
    }
    return intentNodes.filter((item) =>
      [item.name, item.id, item.description, item.fullPath, item.collectionName, item.kind, item.level]
        .filter(Boolean)
        .join(" ")
        .toLowerCase()
        .includes(keyword)
    );
  }, [intentNodes, intentSearch]);

  const intentTree = useMemo(() => buildIntentTree(filteredIntentNodes), [filteredIntentNodes]);

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

  async function refreshConversations(nextUserId = userId) {
    if (!nextUserId) {
      setConversations([]);
      return;
    }
    try {
      const records = await fetchConversations(nextUserId);
      setConversations(records ?? []);
      setNotice("会话列表已刷新");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新会话失败");
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

  async function refreshKnowledgeBaseDetail(kbId = selectedKbId) {
    if (!kbId) {
      setSelectedKbDetail(null);
      return;
    }
    try {
      const detail = await fetchKnowledgeBaseDetail(kbId);
      setSelectedKbDetail(detail);
      setEditKbName(detail.name);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "加载知识库详情失败");
    }
  }

  async function refreshIntentNodes(nextSelectedIntentId = selectedIntentId) {
    setIntentLoading(true);
    try {
      const records = await fetchIntentNodes();
      setIntentNodes(records ?? []);
      const fallbackId = records?.[0]?.recordId ?? "";
      const preferredId =
        nextSelectedIntentId && (records ?? []).some((item) => item.recordId === nextSelectedIntentId)
          ? nextSelectedIntentId
          : fallbackId;
      setSelectedIntentId(preferredId);
      if (preferredId) {
        const detail = await fetchIntentNodeDetail(preferredId);
        setSelectedIntentDetail(detail);
      } else {
        setSelectedIntentDetail(null);
      }
      setNotice("意图列表已刷新");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新意图列表失败");
    } finally {
      setIntentLoading(false);
    }
  }

  async function handleSelectIntent(recordId: string) {
    setSelectedIntentId(recordId);
    setIntentLoading(true);
    try {
      const detail = await fetchIntentNodeDetail(recordId);
      setSelectedIntentDetail(detail);
      setNotice(`已加载意图节点：${detail.name}`);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "加载意图详情失败");
    } finally {
      setIntentLoading(false);
    }
  }

  async function openDocumentDetail(docId: string) {
    setLoading(true);
    try {
      const [detail, chunks] = await Promise.all([fetchKnowledgeDocumentDetail(docId), fetchKnowledgeChunks(docId)]);
      setSelectedDocDetail(detail);
      setDocChunks(chunks ?? []);
      setDocFormName(detail.docName ?? "");
      setDocFormEnabled(detail.enabled !== 0);
      setDocFormScheduleEnabled(detail.scheduleEnabled === 1);
      setDocFormScheduleCron(detail.scheduleCron ?? "0 0/30 * * * ?");
      setDocFormChunkStrategy(detail.chunkStrategy ?? "fixed_size");
      setDocFormChunkConfig(detail.chunkConfig ?? DEFAULT_CHUNK_CONFIG);
      setNewChunkContent("");
      setDocDetailOpen(true);
      setNotice("文档详情已加载");
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "加载文档详情失败");
    } finally {
      setLoading(false);
    }
  }

  async function refreshDocumentDetail(docId = selectedDocDetail?.id) {
    if (!docId) {
      return;
    }
    try {
      const [detail, chunks] = await Promise.all([fetchKnowledgeDocumentDetail(docId), fetchKnowledgeChunks(docId)]);
      setSelectedDocDetail(detail);
      setDocChunks(chunks ?? []);
      setDocFormName(detail.docName ?? "");
      setDocFormEnabled(detail.enabled !== 0);
      setDocFormScheduleEnabled(detail.scheduleEnabled === 1);
      setDocFormScheduleCron(detail.scheduleCron ?? "0 0/30 * * * ?");
      setDocFormChunkStrategy(detail.chunkStrategy ?? "fixed_size");
      setDocFormChunkConfig(detail.chunkConfig ?? DEFAULT_CHUNK_CONFIG);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新文档详情失败");
    }
  }

  useEffect(() => {
    if (!token) {
      setAppView("login");
      return;
    }
    setAppView((current) => (current === "login" ? "chat" : current));
  }, [token]);

  useEffect(() => {
    if (token) {
      void refreshBases();
      void refreshConversations();
      return;
    }
    setNotice("请先登录或填写 s-token");
  }, [token]);

  useEffect(() => {
    void refreshDocuments(selectedKbId);
    void refreshKnowledgeBaseDetail(selectedKbId);
  }, [selectedKbId]);

  useEffect(() => {
    if (token && appView === "intent") {
      void refreshIntentNodes();
    }
  }, [appView, token]);

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    try {
      const response = await login(userName, password);
      setToken(response.token);
      setTokenDraft(response.token);
      setStoredToken(response.token);
      setAppView("chat");
      const nextUserId = response.userId ?? "";
      setUserId(nextUserId);
      if (nextUserId) {
        localStorage.setItem(USER_ID_KEY, nextUserId);
      }
      setNotice("登录成功，s-token 已保存");
      await refreshBases();
      await refreshConversations(nextUserId);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "登录失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleSaveToken() {
    const nextToken = tokenDraft.trim();
    if (!nextToken) {
      try {
        await logout();
      } catch (error) {
        setNotice(error instanceof Error ? error.message : "退出登录失败");
      }
    }
    setToken(nextToken);
    setStoredToken(nextToken);
    setNotice(nextToken ? "s-token 已保存" : "s-token 已清空");
    if (!nextToken) {
      setBases([]);
      setSelectedKbId("");
      setDocuments([]);
      setIntentNodes([]);
      setSelectedIntentId("");
      setSelectedIntentDetail(null);
      setConversations([]);
      setUserId("");
      localStorage.removeItem(USER_ID_KEY);
      setKbDetailOpen(false);
      setConversationId(createConversationId());
      setMessages([]);
      setAppView("login");
      return;
    }
    setAppView("chat");
  }

  function handleNewConversation() {
    void stopActiveChat(false);
    setConversationId(createConversationId());
    setMessages([]);
    setQuestion("");
    setChatLoading(false);
    setNotice("已创建新对话");
  }

  function renderIntentTree(items: IntentTreeNode[], depth = 0): ReactNode {
    if (!items.length) {
      return null;
    }
    return items.map((item) => (
      <div key={item.recordId} className="intent-tree-node">
        <button
          type="button"
          className={`intent-tree-item ${selectedIntentId === item.recordId ? "active" : ""}`}
          style={{ paddingLeft: `${16 + depth * 20}px` }}
          onClick={() => void handleSelectIntent(item.recordId)}
        >
          <span className="intent-tree-main">
            <strong>{item.name}</strong>
            <small>
              {intentLevelText(item.level)} · {intentKindText(item.kind)}
            </small>
          </span>
          <span className="intent-tree-id">{item.id}</span>
        </button>
        {renderIntentTree(item.treeChildren, depth + 1)}
      </div>
    ));
  }

  async function handleSelectConversation(conversation: Conversation) {
    await stopActiveChat(false);
    setConversationId(conversation.id);
    setQuestion("");
    setDeepThinking(conversation.deepThinking === 1);
    setChatLoading(false);
    try {
      const historyMessages = await fetchConversationMessages(conversation.id);
      setMessages((historyMessages ?? []).map((item) => toChatHistoryMessage(item.role, item.content)));
      setNotice(`已切换到会话：${conversation.title || conversation.id}`);
    } catch (error) {
      setMessages([]);
      setNotice(error instanceof Error ? error.message : "加载历史会话失败");
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

  async function handleUpdateKb(event: FormEvent) {
    event.preventDefault();
    if (!selectedKbId || !editKbName.trim()) {
      setNotice("请填写知识库名称");
      return;
    }
    setLoading(true);
    try {
      await updateKnowledgeBase(selectedKbId, { name: editKbName.trim() });
      setNotice("知识库已更新");
      setEditKbOpen(false);
      await refreshBases();
      await refreshKnowledgeBaseDetail(selectedKbId);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "更新知识库失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleDeleteKb() {
    if (!selectedKbId || !selectedKbDetail) {
      return;
    }
    setLoading(true);
    try {
      await deleteKnowledgeBase(selectedKbId);
      setNotice("知识库已删除");
      setKbDetailOpen(false);
      setSelectedKbDetail(null);
      setSelectedKbId("");
      await refreshBases();
      setDocuments([]);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除知识库失败");
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

  async function handleUpdateDocument(event: FormEvent) {
    event.preventDefault();
    if (!selectedDocDetail) {
      return;
    }
    setLoading(true);
    try {
      await updateKnowledgeDocument(selectedDocDetail.id, {
        docName: docFormName.trim(),
        enabled: docFormEnabled,
        scheduleEnabled: docFormScheduleEnabled,
        scheduleCron: docFormScheduleEnabled ? docFormScheduleCron : "",
        chunkStrategy: docFormChunkStrategy,
        chunkConfig: docFormChunkConfig
      });
      setNotice("文档已更新");
      await refreshDocuments();
      await refreshDocumentDetail(selectedDocDetail.id);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "更新文档失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleCreateChunk(event: FormEvent) {
    event.preventDefault();
    if (!selectedDocDetail || !newChunkContent.trim()) {
      setNotice("请输入 Chunk 内容");
      return;
    }
    setLoading(true);
    try {
      await createKnowledgeChunk(selectedDocDetail.id, {
        content: newChunkContent.trim(),
        index: docChunks.length
      });
      setNotice("Chunk 已新增");
      setNewChunkContent("");
      await refreshDocuments();
      await refreshDocumentDetail(selectedDocDetail.id);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "新增 Chunk 失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleUpdateChunk(chunkId: string, content: string) {
    if (!selectedDocDetail || !content.trim()) {
      setNotice("Chunk 内容不能为空");
      return;
    }
    setLoading(true);
    try {
      await updateKnowledgeChunk(selectedDocDetail.id, chunkId, { content: content.trim() });
      setNotice("Chunk 已更新");
      await refreshDocumentDetail(selectedDocDetail.id);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "更新 Chunk 失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleDeleteChunk(chunkId: string) {
    if (!selectedDocDetail) {
      return;
    }
    setLoading(true);
    try {
      await deleteKnowledgeChunk(selectedDocDetail.id, chunkId);
      setNotice("Chunk 已删除");
      await refreshDocuments();
      await refreshDocumentDetail(selectedDocDetail.id);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除 Chunk 失败");
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

    await stopActiveChat(false);
    const controller = new AbortController();
    const assistantMessageId = createMessageId();
    abortRef.current = controller;
    setQuestion("");
    setChatLoading(true);
    setMessages((current) => [
      ...current,
      { id: createMessageId(), role: "user", content: userQuestion },
      {
        id: assistantMessageId,
        role: "assistant",
        content: "",
        thinkingContent: "",
        thinkingCollapsed: false
      }
    ]);

    try {
      await streamChat(
        { userQuestion, conversationId, deepThinking },
        {
          onTask: (taskId) => {
            taskIdRef.current = taskId;
          },
          onToken: (text) => {
            setMessages((current) => {
              return current.map((message) =>
                message.id === assistantMessageId
                  ? { ...message, content: `${message.content}${text}` }
                  : message
              );
            });
          },
          onThinking: (text) => {
            setMessages((current) => {
              return current.map((message) =>
                message.id === assistantMessageId
                  ? {
                      ...message,
                      thinkingCollapsed: false,
                      thinkingStartedAt: message.thinkingStartedAt ?? Date.now(),
                      thinkingContent: `${message.thinkingContent ?? ""}${text}`
                    }
                  : message
              );
            });
          },
          onError: (message) => {
            setNotice(message);
          },
          onCancelled: (message) => {
            finalizeAssistantThinking(assistantMessageId);
            setNotice(message);
          },
          onDone: () => {
            finalizeAssistantThinking(assistantMessageId);
            setChatLoading(false);
            taskIdRef.current = "";
            void refreshConversations();
          }
        },
        controller.signal
      );
    } catch (error) {
      if (!controller.signal.aborted) {
        setNotice(error instanceof Error ? error.message : "聊天请求失败");
      }
    } finally {
      finalizeAssistantThinking(assistantMessageId);
      taskIdRef.current = "";
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

  async function stopChat() {
    await stopActiveChat(true);
  }

  async function handleLogout() {
    await stopActiveChat(false);
    setTokenDraft("");
    await handleSaveToken();
  }

  if (appView === "login") {
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

  if (appView === "chat") {
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

          <button type="button" className="outline-button sidebar-logout" onClick={() => void handleLogout()}>
            <LogOut size={16} />
            退出登录
          </button>

          <section className="quick-card">
            <div className="quick-card-head">
              <span>快速开始</span>
              <b>新内容</b>
            </div>
            <button type="button" className="new-chat-card" onClick={handleNewConversation}>
              <span className="plus-cube">
                <Plus size={22} />
              </span>
              <span>
                <strong>新建对话</strong>
                <small>从空白开始</small>
              </span>
            </button>
            <button type="button" className="mini-pill" onClick={() => setAppView("knowledge")}>
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
                value={conversationSearch}
                onChange={(event) => setConversationSearch(event.target.value)}
                placeholder="搜索对话..."
              />
            </label>
          </section>

          {filteredConversations.length > 0 ? (
            <section className="conversation-list">
              <div className="conversation-list-head">
                <span>最近对话</span>
                <button type="button" onClick={() => void refreshConversations()}>
                  <RefreshCw size={15} />
                </button>
              </div>
              <div className="conversation-items">
                {filteredConversations.map((conversation) => (
                  <button
                    type="button"
                    key={conversation.id}
                    className={`conversation-item ${conversation.id === conversationId ? "active" : ""}`}
                    onClick={() => handleSelectConversation(conversation)}
                  >
                    <MessageSquareText size={17} />
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
              <MessageSquare size={54} />
              <span>{userId ? "暂无对话记录" : "登录后显示对话记录"}</span>
            </div>
          )}

          <div className="chat-user">
            <span className="user-avatar">A</span>
            <strong>{userName}</strong>
            <span>•••</span>
          </div>
        </aside>

        <section className="chat-main">
          <header className="chat-topbar">
            <strong>{conversations.find((item) => item.id === conversationId)?.title || "新对话"}</strong>
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
                  <article className={`chat-turn ${message.role}`} key={message.id}>
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
                        <div className="assistant-body">
                          {message.thinkingContent ? (
                            <div className="assistant-thinking-panel">
                              <button
                                type="button"
                                className={`assistant-thinking-toggle ${message.thinkingCollapsed ? "collapsed" : ""}`}
                                onClick={() => toggleThinking(message.id)}
                                aria-expanded={!message.thinkingCollapsed}
                              >
                                <span className="assistant-thinking-summary">
                                  <Brain size={16} />
                                  <span>
                                    {message.thinkingDurationSeconds
                                      ? `已思考（用时 ${message.thinkingDurationSeconds} 秒）`
                                      : "深度思考中..."}
                                  </span>
                                </span>
                                <ChevronDown className="assistant-thinking-arrow" size={16} />
                              </button>
                              {!message.thinkingCollapsed ? (
                                <div className="assistant-thinking">{message.thinkingContent}</div>
                              ) : null}
                            </div>
                          ) : null}
                          <div className="assistant-text">
                            {message.content ||
                              (chatLoading && index === messages.length - 1
                                ? message.thinkingContent
                                  ? "正在整理最终回答..."
                                  : "正在思考..."
                                : "")}
                          </div>
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
          <button type="button" className="nav-item" onClick={() => setAppView("chat")}>
            <Home size={19} />
            新对话
          </button>
          <button type="button" className="nav-item">
            <BarChart3 size={19} />
            Dashboard
          </button>
          <button type="button" className={`nav-item ${appView === "knowledge" ? "active" : ""}`} onClick={() => setAppView("knowledge")}>
            <Database size={19} />
            知识库管理
          </button>
          <button type="button" className={`nav-item ${appView === "intent" ? "active" : ""}`} onClick={() => setAppView("intent")}>
            <Layers3 size={19} />
            意图管理
            <ChevronDown size={16} />
          </button>
          <button type="button" className={`nav-item sub ${appView === "intent" ? "active" : ""}`} onClick={() => setAppView("intent")}>
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
            <input
              value={appView === "intent" ? intentSearch : kbSearch}
              onChange={(event) => (appView === "intent" ? setIntentSearch(event.target.value) : setKbSearch(event.target.value))}
              placeholder={appView === "intent" ? "筛选意图节点..." : "筛选知识库..."}
            />
            <kbd>Ctrl K</kbd>
          </label>
          <div className="admin-top-actions">
            <button type="button" className="outline-button" onClick={() => setAppView("chat")}>
              <MessageSquare size={18} />
              返回聊天
            </button>
            <button type="button" className="outline-button" onClick={() => void handleLogout()}>
              <LogOut size={18} />
              退出登录
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
          <div className="breadcrumb">首页 / {appView === "intent" ? "意图管理 / 意图列表" : "知识库管理"}</div>
          {appView === "intent" ? (
            <>
              <div className="page-heading">
                <div>
                  <h1>意图列表</h1>
                  <p>查看意图树节点结构，并加载单个节点详情</p>
                </div>
                <div className="page-actions">
                  <button type="button" className="outline-button" onClick={() => void refreshIntentNodes()}>
                    <RefreshCw size={18} className={intentLoading ? "spin" : ""} />
                    刷新
                  </button>
                </div>
              </div>

              <section className="stats-grid">
                <StatCard icon={<Layers3 size={24} />} label="意图节点" value={intentNodes.length} />
                <StatCard icon={<CheckCircle2 size={24} />} label="启用节点" value={enabledIntentCount} />
                <StatCard icon={<Database size={24} />} label="知识库节点" value={intentNodes.filter((item) => item.kind === "KB").length} />
                <StatCard icon={<Sparkles size={24} />} label="根节点" value={intentNodes.filter((item) => !item.parentId).length} />
              </section>

              <section className="intent-layout">
                <section className="admin-card knowledge-overview">
                  <div className="section-head">
                    <strong>意图树</strong>
                    <span>{filteredIntentNodes.length} 个</span>
                  </div>
                  <div className="intent-tree-panel">
                    {intentTree.length ? renderIntentTree(intentTree) : <div className="empty-panel">暂无意图节点</div>}
                  </div>
                </section>

                <section className="admin-card knowledge-detail">
                  <div className="document-panel">
                    <div className="document-panel-head">
                      <div>
                        <span className="panel-eyebrow">节点详情</span>
                        <strong>{selectedIntentDetail?.name ?? "请选择左侧意图节点"}</strong>
                        <small>{selectedIntentDetail?.fullPath ?? "点击左侧节点后查看完整路径、描述与示例问题"}</small>
                      </div>
                    </div>
                    {selectedIntentDetail ? (
                      <div className="intent-detail-grid">
                        <div className="doc-table-wrap">
                          <div className="intent-meta-grid">
                            <IntentMeta label="业务 ID" value={selectedIntentDetail.id} />
                            <IntentMeta label="数据库 ID" value={selectedIntentDetail.recordId} />
                            <IntentMeta label="节点类型" value={intentKindText(selectedIntentDetail.kind)} />
                            <IntentMeta label="节点层级" value={intentLevelText(selectedIntentDetail.level)} />
                            <IntentMeta label="是否启用" value={selectedIntentDetail.enabled === 1 ? "启用" : "停用"} />
                            <IntentMeta label="父节点" value={selectedIntentDetail.parentId || "-"} />
                            <IntentMeta label="知识库 ID" value={selectedIntentDetail.kbId || "-"} />
                            <IntentMeta label="Collection" value={selectedIntentDetail.collectionName || "-"} />
                            <IntentMeta label="MCP 工具" value={selectedIntentDetail.mcpToolId || "-"} />
                            <IntentMeta label="TopK" value={selectedIntentDetail.topK != null ? String(selectedIntentDetail.topK) : "-"} />
                            <IntentMeta label="排序值" value={selectedIntentDetail.sortOrder != null ? String(selectedIntentDetail.sortOrder) : "-"} />
                            <IntentMeta label="子节点数" value={String(selectedIntentDetail.children?.length ?? 0)} />
                          </div>
                        </div>
                        <div className="doc-table-wrap">
                          <div className="intent-detail-section">
                            <strong>节点描述</strong>
                            <p>{selectedIntentDetail.description || "暂无描述"}</p>
                          </div>
                        </div>
                        <div className="doc-table-wrap">
                          <div className="intent-detail-section">
                            <strong>示例问题</strong>
                            {selectedIntentDetail.examples?.length ? (
                              <div className="intent-chip-list">
                                {selectedIntentDetail.examples.map((example, index) => (
                                  <span key={`${selectedIntentDetail.recordId}-example-${index}`} className="intent-chip">
                                    {example}
                                  </span>
                                ))}
                              </div>
                            ) : (
                              <p>暂无示例问题</p>
                            )}
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="empty-panel">请选择左侧节点查看详情</div>
                    )}
                  </div>
                </section>
              </section>
            </>
          ) : (
            <>
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
                        <strong>{selectedKbDetail ? `${selectedKbDetail.name} 的文档` : "请选择知识库"}</strong>
                        <small>{selectedKbDetail?.collectionName ?? "选择左侧知识库后查看和上传文档"}</small>
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
                        <button type="button" className="outline-button small" disabled={!selectedKbDetail} onClick={() => setEditKbOpen(true)}>
                          <Pencil size={15} />
                          编辑知识库
                        </button>
                        <button type="button" className="outline-button small danger-text" disabled={!selectedKbDetail} onClick={() => void handleDeleteKb()}>
                          <Trash2 size={15} />
                          删除知识库
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
                              <button type="button" onClick={() => void openDocumentDetail(doc.id)}>
                                详情
                              </button>
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
            </>
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

      {editKbOpen && selectedKbDetail && (
        <Modal title="编辑知识库" description={`更新「${selectedKbDetail.name}」的基础信息`} onClose={() => setEditKbOpen(false)}>
          <form className="modal-form" onSubmit={handleUpdateKb}>
            <label>
              <span>知识库名称</span>
              <input value={editKbName} onChange={(event) => setEditKbName(event.target.value)} placeholder="请输入知识库名称" />
            </label>
            <label>
              <span>Embedding 模型</span>
              <input value={selectedKbDetail.embeddingModel} disabled />
            </label>
            <label>
              <span>Collection 名称</span>
              <input value={selectedKbDetail.collectionName} disabled />
            </label>
            <div className="modal-actions">
              <button type="button" className="outline-button" onClick={() => setEditKbOpen(false)}>
                取消
              </button>
              <button type="submit" className="gradient-button" disabled={loading}>
                {loading ? <Loader2 className="spin" size={16} /> : <Pencil size={16} />}
                保存
              </button>
            </div>
          </form>
        </Modal>
      )}

      {docDetailOpen && selectedDocDetail && (
        <Modal title="文档详情" description={`查看并管理「${selectedDocDetail.docName}」`} onClose={() => setDocDetailOpen(false)}>
          <form className="modal-form" onSubmit={handleUpdateDocument}>
            <label>
              <span>文档名称</span>
              <input value={docFormName} onChange={(event) => setDocFormName(event.target.value)} placeholder="请输入文档名称" />
            </label>
            <label className="toggle modal-toggle">
              <input checked={docFormEnabled} onChange={(event) => setDocFormEnabled(event.target.checked)} type="checkbox" />
              启用文档
            </label>
            <label className="toggle modal-toggle">
              <input checked={docFormScheduleEnabled} onChange={(event) => setDocFormScheduleEnabled(event.target.checked)} type="checkbox" />
              启用定时拉取
            </label>
            {docFormScheduleEnabled && (
              <label>
                <span>定时表达式</span>
                <input value={docFormScheduleCron} onChange={(event) => setDocFormScheduleCron(event.target.value)} />
              </label>
            )}
            <label>
              <span>分块策略</span>
              <select value={docFormChunkStrategy} onChange={(event) => setDocFormChunkStrategy(event.target.value)}>
                <option value="fixed_size">fixed_size</option>
                <option value="structure_aware">structure_aware</option>
              </select>
            </label>
            <label>
              <span>分块参数 JSON</span>
              <textarea value={docFormChunkConfig} onChange={(event) => setDocFormChunkConfig(event.target.value)} rows={4} />
            </label>
            <div className="modal-actions">
              <button type="button" className="outline-button" onClick={() => setDocDetailOpen(false)}>
                关闭
              </button>
              <button type="submit" className="gradient-button" disabled={loading}>
                {loading ? <Loader2 className="spin" size={16} /> : <Pencil size={16} />}
                保存文档
              </button>
            </div>
          </form>

          <div className="section-head">
            <strong>Chunk 列表</strong>
            <span>{docChunks.length} 条</span>
          </div>
          <form className="modal-form" onSubmit={handleCreateChunk}>
            <label>
              <span>新增 Chunk</span>
              <textarea value={newChunkContent} onChange={(event) => setNewChunkContent(event.target.value)} rows={4} placeholder="输入新的 chunk 内容" />
            </label>
            <div className="modal-actions">
              <button type="submit" className="gradient-button" disabled={loading}>
                {loading ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
                新增 Chunk
              </button>
            </div>
          </form>
          <div className="chunk-editor-list">
            {docChunks.length ? (
              docChunks.map((chunk, index) => (
                <div className="chunk-editor-card" key={chunk.id}>
                  <div className="chunk-editor-head">
                    <strong>Chunk #{chunk.chunkIndex ?? index}</strong>
                    <small>{chunk.charCount ?? chunk.content.length} 字</small>
                  </div>
                  <textarea
                    value={chunk.content}
                    onChange={(event) =>
                      setDocChunks((current) =>
                        current.map((item) => (item.id === chunk.id ? { ...item, content: event.target.value } : item))
                      )
                    }
                    rows={5}
                  />
                  <div className="modal-actions">
                    <button type="button" className="outline-button" onClick={() => void handleUpdateChunk(chunk.id, chunk.content)}>
                      保存 Chunk
                    </button>
                    <button type="button" className="outline-button danger-text" onClick={() => void handleDeleteChunk(chunk.id)}>
                      删除 Chunk
                    </button>
                  </div>
                </div>
              ))
            ) : (
              <div className="empty-panel">暂无 Chunk，可先手动新增或执行自动分块</div>
            )}
          </div>
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

function IntentMeta({ label, value }: { label: string; value: string }) {
  return (
    <div className="intent-meta-item">
      <small>{label}</small>
      <strong>{value}</strong>
    </div>
  );
}

function BookIcon({ size = 20 }: { size?: number }) {
  return <FileText size={size} />;
}
