import {
  ArrowLeft,
  RefreshCw,
  UploadCloud
} from "lucide-react";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { DocumentDetailModal } from "../components/DocumentDetailModal";
import { DocumentsTable } from "../components/DocumentsTable";
import { UploadDocumentModal } from "../components/UploadDocumentModal";
import { useKnowledgeDocumentsPage } from "../hooks/useKnowledgeDocumentsPage";

export function KnowledgeDocumentsPage() {
  const navigate = useNavigate();
  const page = useKnowledgeDocumentsPage();

  useEffect(() => {
    void page.refreshPage();
  }, [page.kbId]);

  return (
    <div className="page-shell">
      <div className="page-head">
        <div>
          <span className="eyebrow">Knowledge Documents</span>
          <h2>{page.kbDetail ? `${page.kbDetail.name} / 文档管理` : "文档管理"}</h2>
        </div>
        <div className="page-actions">
          <button type="button" className="outline-button" onClick={() => navigate("/knowledge")}>
            <ArrowLeft size={16} />
            返回知识库
          </button>
          <button type="button" className="outline-button" onClick={() => void page.refreshPage()}>
            <RefreshCw size={16} />
            刷新
          </button>
          <button type="button" className="gradient-button" onClick={() => page.setUploadOpen(true)}>
            <UploadCloud size={16} />
            上传文档
          </button>
        </div>
      </div>

      <div className="page-actions secondary">
        <input value={page.search} onChange={(event) => page.setSearch(event.target.value)} placeholder="搜索文档名称 / 来源" />
        <select value={page.statusFilter} onChange={(event) => page.setStatusFilter(event.target.value)}>
          <option value="all">全部状态</option>
          <option value="pending">待处理</option>
          <option value="running">处理中</option>
          <option value="success">完成</option>
          <option value="failed">失败</option>
        </select>
      </div>

      <p className="notice-text">{page.notice}</p>

      <DocumentsTable
        documents={page.filteredDocuments}
        onOpenDetail={(docId) => void page.openDocumentDetail(docId)}
        onChunk={(docId) => void page.handleChunk(docId)}
        onDelete={(docId) => void page.handleDeleteDoc(docId)}
      />

      <UploadDocumentModal
        open={page.uploadOpen}
        loading={page.loading}
        kbDetail={page.kbDetail}
        sourceType={page.sourceType}
        sourceUrl={page.sourceUrl}
        scheduleEnabled={page.scheduleEnabled}
        scheduleCron={page.scheduleCron}
        chunkStrategy={page.chunkStrategy}
        chunkConfig={page.chunkConfig}
        onClose={() => page.setUploadOpen(false)}
        onSubmit={(event) => void page.handleUpload(event)}
        onSourceTypeChange={page.setSourceType}
        onSourceUrlChange={page.setSourceUrl}
        onFileChange={page.setFile}
        onScheduleEnabledChange={page.setScheduleEnabled}
        onScheduleCronChange={page.setScheduleCron}
        onChunkStrategyChange={page.setChunkStrategy}
        onChunkConfigChange={page.setChunkConfig}
      />

      <DocumentDetailModal
        open={page.docDetailOpen}
        loading={page.loading}
        docDetail={page.selectedDocDetail}
        docChunks={page.docChunks}
        docFormName={page.docFormName}
        docFormEnabled={page.docFormEnabled}
        docFormScheduleEnabled={page.docFormScheduleEnabled}
        docFormScheduleCron={page.docFormScheduleCron}
        docFormChunkStrategy={page.docFormChunkStrategy}
        docFormChunkConfig={page.docFormChunkConfig}
        newChunkContent={page.newChunkContent}
        onClose={() => page.setDocDetailOpen(false)}
        onSubmitDocument={(event) => void page.handleUpdateDocument(event)}
        onSubmitChunk={(event) => void page.handleCreateChunk(event)}
        onDocFormNameChange={page.setDocFormName}
        onDocFormEnabledChange={page.setDocFormEnabled}
        onDocFormScheduleEnabledChange={page.setDocFormScheduleEnabled}
        onDocFormScheduleCronChange={page.setDocFormScheduleCron}
        onDocFormChunkStrategyChange={page.setDocFormChunkStrategy}
        onDocFormChunkConfigChange={page.setDocFormChunkConfig}
        onNewChunkContentChange={page.setNewChunkContent}
        onChunkContentChange={(chunkId, value) =>
          page.setDocChunks((current) =>
            current.map((item) => (item.id === chunkId ? { ...item, content: value } : item))
          )
        }
        onUpdateChunk={(chunkId, content) => void page.handleUpdateChunk(chunkId, content)}
        onDeleteChunk={(chunkId) => void page.handleDeleteChunk(chunkId)}
      />
    </div>
  );
}
