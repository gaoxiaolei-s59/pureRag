import { FileText, Trash2 } from "lucide-react";
import { EmptyState } from "../../../components/common/EmptyState";
import { KnowledgeDocument } from "../types";
import { formatBytes, formatDateTime, statusText } from "../utils";

type DocumentsTableProps = {
  documents: KnowledgeDocument[];
  onOpenDetail: (docId: string) => void;
  onChunk: (docId: string) => void;
  onDelete: (docId: string) => void;
};

export function DocumentsTable({
  documents,
  onOpenDetail,
  onChunk,
  onDelete
}: DocumentsTableProps) {
  return (
    <section className="data-card">
      <div className="table-grid docs-grid table-header">
        <span>文档名</span>
        <span>来源</span>
        <span>大小</span>
        <span>Chunk 数</span>
        <span>状态</span>
        <span>创建时间</span>
        <span>操作</span>
      </div>
      {documents.length ? (
        documents.map((doc) => (
          <div className="table-grid docs-grid table-row" key={doc.id}>
            <span>{doc.docName}</span>
            <span>{doc.sourceLocation || doc.sourceType}</span>
            <span>{formatBytes(doc.fileSize)}</span>
            <span>{doc.chunkCount ?? 0}</span>
            <span>{statusText(doc.status)}</span>
            <span>{formatDateTime(doc.createTime)}</span>
            <span className="row-actions">
              <button type="button" className="outline-button small" onClick={() => onOpenDetail(doc.id)}>
                详情
              </button>
              <button type="button" className="outline-button small" onClick={() => onChunk(doc.id)}>
                分块
              </button>
              <button type="button" className="outline-button small danger-text" onClick={() => onDelete(doc.id)}>
                <Trash2 size={14} />
                删除
              </button>
            </span>
          </div>
        ))
      ) : (
        <EmptyState
          icon={<FileText size={30} />}
          title="暂无文档"
          description="上传文档后，这里会显示处理状态和 chunk 信息。"
        />
      )}
    </section>
  );
}
