import { FormEvent, useMemo, useState } from "react";
import { useParams } from "react-router-dom";
import { buildKnowledgeDocumentUpdatePayload, createKnowledgeDocumentFormState } from "../documentForm";
import { filterDocuments } from "../selectors";
import {
  createKnowledgeChunk,
  deleteKnowledgeChunk,
  deleteKnowledgeDocument,
  fetchKnowledgeBaseDetail,
  fetchKnowledgeChunks,
  fetchKnowledgeDocumentDetail,
  fetchKnowledgeDocuments,
  startDocumentChunk,
  updateKnowledgeChunk,
  updateKnowledgeDocument,
  uploadKnowledgeDocument
} from "../services/knowledge";
import { KnowledgeBase, KnowledgeChunk, KnowledgeDocument } from "../types";
import { DEFAULT_CHUNK_CONFIG } from "../utils";

export function useKnowledgeDocumentsPage() {
  const { kbId = "" } = useParams();
  const [notice, setNotice] = useState("管理文档上传、分块和 chunk 细节");
  const [loading, setLoading] = useState(false);
  const [kbDetail, setKbDetail] = useState<KnowledgeBase | null>(null);
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [selectedDocDetail, setSelectedDocDetail] = useState<KnowledgeDocument | null>(null);
  const [docChunks, setDocChunks] = useState<KnowledgeChunk[]>([]);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("all");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [docDetailOpen, setDocDetailOpen] = useState(false);
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

  const filteredDocuments = useMemo(
    () => filterDocuments(documents, search, statusFilter),
    [documents, search, statusFilter]
  );

  /**
   * 刷新文档管理页的主数据。
   * 这里统一拉取知识库详情和文档列表，保证头部信息与表格内容来自同一轮请求。
   */
  async function refreshPage() {
    if (!kbId) {
      return;
    }
    setLoading(true);
    try {
      const [base, page] = await Promise.all([fetchKnowledgeBaseDetail(kbId), fetchKnowledgeDocuments(kbId)]);
      setKbDetail(base);
      setDocuments(page.records ?? []);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "加载知识库文档失败");
    } finally {
      setLoading(false);
    }
  }

  /**
   * 拉取单个文档的详情和 chunk 列表，并把结果同步到详情弹窗表单。
   * `openPanel` 用于区分首次打开和局部刷新，避免页面层关心表单如何从接口数据映射。
   */
  async function loadDocumentDetail(docId: string, openPanel: boolean) {
    const [detail, chunks] = await Promise.all([fetchKnowledgeDocumentDetail(docId), fetchKnowledgeChunks(docId)]);
    const formState = createKnowledgeDocumentFormState(detail);
    setSelectedDocDetail(detail);
    setDocChunks(chunks ?? []);
    setDocFormName(formState.docFormName);
    setDocFormEnabled(formState.docFormEnabled);
    setDocFormScheduleEnabled(formState.docFormScheduleEnabled);
    setDocFormScheduleCron(formState.docFormScheduleCron);
    setDocFormChunkStrategy(formState.docFormChunkStrategy);
    setDocFormChunkConfig(formState.docFormChunkConfig);
    setNewChunkContent("");
    if (openPanel) {
      setDocDetailOpen(true);
    }
  }

  /**
   * 打开文档详情弹窗，并同步文档编辑表单与 chunk 列表。
   */
  async function openDocumentDetail(docId: string) {
    setLoading(true);
    try {
      await loadDocumentDetail(docId, true);
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
      await loadDocumentDetail(docId, false);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新文档详情失败");
    }
  }

  /**
   * 上传新文档并在成功后回刷列表。
   * 上传表单状态仍然收口在 hook，弹窗组件只负责视图和输入。
   */
  async function handleUpload(event: FormEvent) {
    event.preventDefault();
    if (!kbId) {
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
        kbId,
        sourceType,
        file,
        sourceLocation: sourceType === "url" ? sourceUrl : undefined,
        scheduleEnabled,
        scheduleCron: scheduleEnabled ? scheduleCron : undefined,
        processMode: "chunk",
        chunkStrategy,
        chunkConfig
      });
      setUploadOpen(false);
      setFile(null);
      setNotice("文档已上传");
      await refreshPage();
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
      await refreshPage();
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
      await refreshPage();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除文档失败");
    } finally {
      setLoading(false);
    }
  }

  /**
   * 更新文档的名称、启用状态和分块配置。
   * 提交参数由纯函数统一组装，降低页面字段散落在请求代码里的耦合度。
   */
  async function handleUpdateDocument(event: FormEvent) {
    event.preventDefault();
    if (!selectedDocDetail) {
      return;
    }
    setLoading(true);
    try {
      await updateKnowledgeDocument(
        selectedDocDetail.id,
        buildKnowledgeDocumentUpdatePayload({
          docFormName,
          docFormEnabled,
          docFormScheduleEnabled,
          docFormScheduleCron,
          docFormChunkStrategy,
          docFormChunkConfig
        })
      );
      setNotice("文档已更新");
      await refreshPage();
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
      setNewChunkContent("");
      setNotice("Chunk 已新增");
      await refreshPage();
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
      await refreshPage();
      await refreshDocumentDetail(selectedDocDetail.id);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除 Chunk 失败");
    } finally {
      setLoading(false);
    }
  }

  return {
    kbId,
    notice,
    loading,
    kbDetail,
    documents,
    filteredDocuments,
    selectedDocDetail,
    docChunks,
    search,
    statusFilter,
    uploadOpen,
    docDetailOpen,
    sourceType,
    sourceUrl,
    file,
    scheduleEnabled,
    scheduleCron,
    chunkStrategy,
    chunkConfig,
    docFormName,
    docFormEnabled,
    docFormScheduleEnabled,
    docFormScheduleCron,
    docFormChunkStrategy,
    docFormChunkConfig,
    newChunkContent,
    refreshPage,
    openDocumentDetail,
    setSearch,
    setStatusFilter,
    setUploadOpen,
    setDocDetailOpen,
    setSourceType,
    setSourceUrl,
    setFile,
    setScheduleEnabled,
    setScheduleCron,
    setChunkStrategy,
    setChunkConfig,
    setDocFormName,
    setDocFormEnabled,
    setDocFormScheduleEnabled,
    setDocFormScheduleCron,
    setDocFormChunkStrategy,
    setDocFormChunkConfig,
    setNewChunkContent,
    setDocChunks,
    handleUpload,
    handleChunk,
    handleDeleteDoc,
    handleUpdateDocument,
    handleCreateChunk,
    handleUpdateChunk,
    handleDeleteChunk
  };
}
