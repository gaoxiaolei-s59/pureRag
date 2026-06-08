import {
  ArrowLeft,
  RefreshCw,
  Search,
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
  const kbSubtitle = page.kbDetail
    ? `${page.kbDetail.name}${page.kbDetail.collectionName ? `（${page.kbDetail.collectionName}）` : ""}`
    : "知识库文档";

  useEffect(() => {
    void page.refreshPage();
  }, [page.kbId]);

  return (
    <div className="page-shell docs-page-shell">
      <div className="page-head docs-page-head">
        <div>
          <nav className="docs-breadcrumb" aria-label="当前位置">
            <span>首页</span>
            <span>/</span>
            <span>知识库管理</span>
            <span>/</span>
            <strong>文档管理</strong>
          </nav>
          <h2>文档管理</h2>
          <p>{kbSubtitle}</p>
        </div>
        <div className="page-actions">
          <button type="button" className="outline-button" onClick={() => navigate("/knowledge")}>
            <ArrowLeft size={16} />
            返回知识库
          </button>
          <button type="button" className="gradient-button" onClick={() => page.setUploadOpen(true)}>
            <UploadCloud size={16} />
            上传文档
          </button>
        </div>
      </div>

      <DocumentsTable
        documents={page.filteredDocuments}
        toolbar={
          <>
            <div className="soft-search docs-search">
              <Search size={18} />
              <input value={page.search} onChange={(event) => page.setSearch(event.target.value)} placeholder="搜索文档名称" />
            </div>
            <button type="button" className="outline-button">
              搜索
            </button>
            <select className="docs-filter-select" value={page.statusFilter} onChange={(event) => page.setStatusFilter(event.target.value)}>
              <option value="all">全部状态</option>
              <option value="pending">待处理</option>
              <option value="running">处理中</option>
              <option value="success">完成</option>
              <option value="failed">失败</option>
            </select>
            <button type="button" className="outline-button" onClick={() => void page.refreshPage()}>
              <RefreshCw size={16} />
              刷新
            </button>
          </>
        }
        onOpenDetail={(docId) => void page.openDocumentDetail(docId)}
        onChunk={(docId) => void page.handleChunk(docId)}
        onDelete={(docId) => void page.handleDeleteDoc(docId)}
      />

      <UploadDocumentModal
        open={page.uploadOpen}
        loading={page.loading}
        kbDetail={page.kbDetail}
        sourceType={page.sourceType}
        file={page.file}
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
        docFormName={page.docFormName}
        docFormEnabled={page.docFormEnabled}
        docFormScheduleEnabled={page.docFormScheduleEnabled}
        docFormScheduleCron={page.docFormScheduleCron}
        docFormChunkStrategy={page.docFormChunkStrategy}
        docFormChunkConfig={page.docFormChunkConfig}
        onClose={() => page.setDocDetailOpen(false)}
        onSubmitDocument={(event) => void page.handleUpdateDocument(event)}
        onDocFormNameChange={page.setDocFormName}
        onDocFormEnabledChange={page.setDocFormEnabled}
        onDocFormScheduleEnabledChange={page.setDocFormScheduleEnabled}
        onDocFormScheduleCronChange={page.setDocFormScheduleCron}
        onDocFormChunkStrategyChange={page.setDocFormChunkStrategy}
        onDocFormChunkConfigChange={page.setDocFormChunkConfig}
      />
    </div>
  );
}
