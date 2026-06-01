import { Pencil, Plus } from "lucide-react";
import { FormEvent } from "react";
import { EmptyState } from "../../../components/common/EmptyState";
import { Modal } from "../../../components/common/Modal";
import { KnowledgeChunk, KnowledgeDocument } from "../types";

type DocumentDetailModalProps = {
  open: boolean;
  loading: boolean;
  docDetail: KnowledgeDocument | null;
  docChunks: KnowledgeChunk[];
  docFormName: string;
  docFormEnabled: boolean;
  docFormScheduleEnabled: boolean;
  docFormScheduleCron: string;
  docFormChunkStrategy: string;
  docFormChunkConfig: string;
  newChunkContent: string;
  onClose: () => void;
  onSubmitDocument: (event: FormEvent) => void;
  onSubmitChunk: (event: FormEvent) => void;
  onDocFormNameChange: (value: string) => void;
  onDocFormEnabledChange: (value: boolean) => void;
  onDocFormScheduleEnabledChange: (value: boolean) => void;
  onDocFormScheduleCronChange: (value: string) => void;
  onDocFormChunkStrategyChange: (value: string) => void;
  onDocFormChunkConfigChange: (value: string) => void;
  onNewChunkContentChange: (value: string) => void;
  onChunkContentChange: (chunkId: string, value: string) => void;
  onUpdateChunk: (chunkId: string, content: string) => void;
  onDeleteChunk: (chunkId: string) => void;
};

export function DocumentDetailModal({
  open,
  loading,
  docDetail,
  docChunks,
  docFormName,
  docFormEnabled,
  docFormScheduleEnabled,
  docFormScheduleCron,
  docFormChunkStrategy,
  docFormChunkConfig,
  newChunkContent,
  onClose,
  onSubmitDocument,
  onSubmitChunk,
  onDocFormNameChange,
  onDocFormEnabledChange,
  onDocFormScheduleEnabledChange,
  onDocFormScheduleCronChange,
  onDocFormChunkStrategyChange,
  onDocFormChunkConfigChange,
  onNewChunkContentChange,
  onChunkContentChange,
  onUpdateChunk,
  onDeleteChunk
}: DocumentDetailModalProps) {
  if (!open || !docDetail) {
    return null;
  }

  return (
    <Modal title="文档详情" description={`查看并管理「${docDetail.docName}」`} onClose={onClose}>
      <form className="modal-form" onSubmit={onSubmitDocument}>
        <label>
          <span>文档名称</span>
          <input value={docFormName} onChange={(event) => onDocFormNameChange(event.target.value)} />
        </label>
        <label className="toggle">
          <input checked={docFormEnabled} onChange={(event) => onDocFormEnabledChange(event.target.checked)} type="checkbox" />
          启用文档
        </label>
        <label className="toggle">
          <input
            checked={docFormScheduleEnabled}
            onChange={(event) => onDocFormScheduleEnabledChange(event.target.checked)}
            type="checkbox"
          />
          启用定时拉取
        </label>
        {docFormScheduleEnabled ? (
          <label>
            <span>定时表达式</span>
            <input value={docFormScheduleCron} onChange={(event) => onDocFormScheduleCronChange(event.target.value)} />
          </label>
        ) : null}
        <label>
          <span>分块策略</span>
          <select value={docFormChunkStrategy} onChange={(event) => onDocFormChunkStrategyChange(event.target.value)}>
            <option value="fixed_size">fixed_size</option>
            <option value="structure_aware">structure_aware</option>
          </select>
        </label>
        <label>
          <span>分块参数 JSON</span>
          <textarea value={docFormChunkConfig} onChange={(event) => onDocFormChunkConfigChange(event.target.value)} rows={4} />
        </label>
        <div className="modal-actions">
          <button type="submit" className="gradient-button" disabled={loading}>
            <Pencil size={16} />
            保存文档
          </button>
        </div>
      </form>

      <div className="section-head">
        <strong>Chunk 列表</strong>
        <span>{docChunks.length} 条</span>
      </div>

      <form className="modal-form" onSubmit={onSubmitChunk}>
        <label>
          <span>新增 Chunk</span>
          <textarea value={newChunkContent} onChange={(event) => onNewChunkContentChange(event.target.value)} rows={4} />
        </label>
        <div className="modal-actions">
          <button type="submit" className="gradient-button" disabled={loading}>
            <Plus size={16} />
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
                onChange={(event) => onChunkContentChange(chunk.id, event.target.value)}
                rows={5}
              />
              <div className="modal-actions">
                <button type="button" className="outline-button" onClick={() => onUpdateChunk(chunk.id, chunk.content)}>
                  保存 Chunk
                </button>
                <button type="button" className="outline-button danger-text" onClick={() => onDeleteChunk(chunk.id)}>
                  删除 Chunk
                </button>
              </div>
            </div>
          ))
        ) : (
          <EmptyState title="暂无 Chunk" description="可以先自动分块，或者手动新增内容块。" />
        )}
      </div>
    </Modal>
  );
}
