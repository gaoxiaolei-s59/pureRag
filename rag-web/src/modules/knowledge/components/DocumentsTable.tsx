import { FileText, MoreHorizontal, Pencil, PlayCircle, Trash2 } from "lucide-react";
import { ReactNode } from "react";
import { EmptyState } from "../../../components/common/EmptyState";
import { KnowledgeDocument } from "../types";
import { formatBytes, formatDateTime, statusText, statusTone } from "../utils";

type DocumentsTableProps = {
  documents: KnowledgeDocument[];
  toolbar?: ReactNode;
  onOpenDetail: (docId: string) => void;
  onChunk: (docId: string) => void;
  onDelete: (docId: string) => void;
};

function sourceText(doc: KnowledgeDocument) {
  if (doc.sourceType === "url") {
    return "Remote URL";
  }
  return "Local File";
}

function fileMetaText(doc: KnowledgeDocument) {
  return [doc.fileType, formatBytes(doc.fileSize), sourceText(doc)].filter(Boolean).join(" · ");
}

function processModeText(doc: KnowledgeDocument) {
  if (doc.processMode === "pipeline") {
    return "Pipeline";
  }
  return doc.chunkStrategy === "structure_aware" ? "Structure" : "Chunk";
}

export function DocumentsTable({
  documents,
  toolbar,
  onOpenDetail,
  onChunk,
  onDelete
}: DocumentsTableProps) {
  return (
    <section className="data-card docs-card">
      <div className="docs-card-head">
        <div>
          <h3>文档列表</h3>
          <p>支持筛选与分块管理</p>
        </div>
        {toolbar ? <div className="docs-card-toolbar">{toolbar}</div> : null}
      </div>

      <div className="docs-table-scroll">
        <div className="table-grid docs-grid table-header docs-table-header">
          <span className="docs-check-cell">
            <input aria-label="选择全部文档" type="checkbox" disabled />
          </span>
          <span>文档</span>
          <span>状态</span>
          <span>启用</span>
          <span>分块数</span>
          <span>处理模式</span>
          <span>更新时间</span>
          <span>操作</span>
        </div>
        {documents.length ? (
          documents.map((doc) => (
            <div className="table-grid docs-grid table-row docs-table-row" key={doc.id}>
              <span className="docs-check-cell">
                <input aria-label={`选择 ${doc.docName}`} type="checkbox" disabled />
              </span>
              <span className="docs-doc-cell">
                <FileText size={18} />
                <span>
                  <strong>{doc.docName}</strong>
                  <small>{fileMetaText(doc)}</small>
                </span>
              </span>
              <span className="docs-status-cell">
                <span className={`status-badge ${statusTone(doc.status)}`}>{statusText(doc.status)}</span>
              </span>
              <span>
                <span className={`docs-enabled-switch ${doc.enabled === 0 ? "" : "active"}`} aria-label={doc.enabled === 0 ? "未启用" : "已启用"} />
              </span>
              <span>{doc.chunkCount ?? "-"}</span>
              <span>{processModeText(doc)}</span>
              <span>
                <span className="docs-updated-meta">{doc.updatedBy || doc.createdBy || "admin"} · {formatDateTime(doc.updateTime || doc.createTime)}</span>
              </span>
              <span className="row-actions">
                <button type="button" className="outline-button small" onClick={() => onOpenDetail(doc.id)}>
                  <Pencil size={14} />
                  编辑
                </button>
                <button type="button" className="outline-button small" onClick={() => onChunk(doc.id)}>
                  <PlayCircle size={14} />
                  分块
                </button>
                <button type="button" className="outline-button small danger-text" onClick={() => onDelete(doc.id)}>
                  <Trash2 size={14} />
                  删除
                </button>
                <button type="button" className="ghost-button small docs-more-button" aria-label="更多操作">
                  <MoreHorizontal size={16} />
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
      </div>
    </section>
  );
}
