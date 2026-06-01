import { Loader2, UploadCloud } from "lucide-react";
import { FormEvent } from "react";
import { Modal } from "../../../components/common/Modal";
import { KnowledgeBase } from "../types";

type UploadDocumentModalProps = {
  open: boolean;
  loading: boolean;
  kbDetail: KnowledgeBase | null;
  sourceType: "file" | "url";
  sourceUrl: string;
  scheduleEnabled: boolean;
  scheduleCron: string;
  chunkStrategy: string;
  chunkConfig: string;
  onClose: () => void;
  onSubmit: (event: FormEvent) => void;
  onSourceTypeChange: (value: "file" | "url") => void;
  onSourceUrlChange: (value: string) => void;
  onFileChange: (file: File | null) => void;
  onScheduleEnabledChange: (value: boolean) => void;
  onScheduleCronChange: (value: string) => void;
  onChunkStrategyChange: (value: string) => void;
  onChunkConfigChange: (value: string) => void;
};

export function UploadDocumentModal({
  open,
  loading,
  kbDetail,
  sourceType,
  sourceUrl,
  scheduleEnabled,
  scheduleCron,
  chunkStrategy,
  chunkConfig,
  onClose,
  onSubmit,
  onSourceTypeChange,
  onSourceUrlChange,
  onFileChange,
  onScheduleEnabledChange,
  onScheduleCronChange,
  onChunkStrategyChange,
  onChunkConfigChange
}: UploadDocumentModalProps) {
  if (!open) {
    return null;
  }

  return (
    <Modal
      title="上传文档"
      description={kbDetail ? `上传到「${kbDetail.name}」并按策略分块` : "请选择知识库后上传文档"}
      onClose={onClose}
    >
      <form className="modal-form" onSubmit={onSubmit}>
        <div className="segmented">
          <button type="button" className={sourceType === "file" ? "active" : ""} onClick={() => onSourceTypeChange("file")}>
            文件
          </button>
          <button type="button" className={sourceType === "url" ? "active" : ""} onClick={() => onSourceTypeChange("url")}>
            URL
          </button>
        </div>
        <label>
          <span>{sourceType === "file" ? "选择文件" : "文档 URL"}</span>
          {sourceType === "file" ? (
            <input type="file" onChange={(event) => onFileChange(event.target.files?.[0] ?? null)} />
          ) : (
            <input value={sourceUrl} onChange={(event) => onSourceUrlChange(event.target.value)} />
          )}
        </label>
        <label>
          <span>分块策略</span>
          <select value={chunkStrategy} onChange={(event) => onChunkStrategyChange(event.target.value)}>
            <option value="fixed_size">fixed_size</option>
            <option value="structure_aware">structure_aware</option>
          </select>
        </label>
        <label>
          <span>分块参数 JSON</span>
          <textarea value={chunkConfig} onChange={(event) => onChunkConfigChange(event.target.value)} rows={4} />
        </label>
        <label className="toggle">
          <input checked={scheduleEnabled} onChange={(event) => onScheduleEnabledChange(event.target.checked)} type="checkbox" />
          定时拉取 URL 文档
        </label>
        {scheduleEnabled ? (
          <label>
            <span>定时表达式</span>
            <input value={scheduleCron} onChange={(event) => onScheduleCronChange(event.target.value)} />
          </label>
        ) : null}
        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose}>
            取消
          </button>
          <button type="submit" className="gradient-button" disabled={loading}>
            {loading ? <Loader2 className="spin" size={16} /> : <UploadCloud size={16} />}
            上传
          </button>
        </div>
      </form>
    </Modal>
  );
}
