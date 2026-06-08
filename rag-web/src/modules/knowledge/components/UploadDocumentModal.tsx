import { FileUp, Loader2, UploadCloud, X } from "lucide-react";
import { DragEvent, FormEvent } from "react";
import { Modal } from "../../../components/common/Modal";
import { KnowledgeBase } from "../types";

type UploadDocumentModalProps = {
  open: boolean;
  loading: boolean;
  kbDetail: KnowledgeBase | null;
  sourceType: "file" | "url";
  file: File | null;
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

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function readChunkConfig(chunkConfig: string) {
  try {
    const parsed = JSON.parse(chunkConfig) as { chunkSize?: number; overlapSize?: number };
    return {
      chunkSize: Number.isFinite(parsed.chunkSize) ? Number(parsed.chunkSize) : 512,
      overlapSize: Number.isFinite(parsed.overlapSize) ? Number(parsed.overlapSize) : 128
    };
  } catch {
    return {
      chunkSize: 512,
      overlapSize: 128
    };
  }
}

export function UploadDocumentModal({
  open,
  loading,
  kbDetail,
  sourceType,
  file,
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

  const chunkValues = readChunkConfig(chunkConfig);
  const noChunk = chunkValues.chunkSize === -1;

  function updateChunkConfig(next: Partial<{ chunkSize: number; overlapSize: number }>) {
    onChunkConfigChange(JSON.stringify({ ...chunkValues, ...next }, null, 2));
  }

  function handleFileDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    onFileChange(event.dataTransfer.files?.[0] ?? null);
  }

  return (
    <Modal
      title="上传文档"
      description={kbDetail ? `支持本地文件或远程URL，并配置分块策略` : "请选择知识库后上传文档"}
      onClose={onClose}
      cardClassName="upload-modal-card"
    >
      <form className="modal-form upload-document-form" onSubmit={onSubmit}>
        <label className="upload-field">
          <span>来源类型</span>
          <select value={sourceType} onChange={(event) => onSourceTypeChange(event.target.value as "file" | "url")}>
            <option value="file">Local File</option>
            <option value="url">Remote URL</option>
          </select>
        </label>

        {sourceType === "file" ? (
          <div className="upload-field">
            <span>本地文件</span>
            {file ? (
              <div className="upload-file-selected">
                <FileUp size={20} className="upload-file-icon" />
                <div className="upload-file-info">
                  <strong className="upload-file-name">{file.name}</strong>
                  <small className="upload-file-size">{formatFileSize(file.size)}</small>
                </div>
                <button
                  type="button"
                  className="upload-file-clear"
                  onClick={() => onFileChange(null)}
                  title="清除文件"
                >
                  <X size={16} />
                </button>
              </div>
            ) : (
              <label
                className="upload-dropzone"
                onDragOver={(event) => event.preventDefault()}
                onDrop={handleFileDrop}
              >
                <FileUp size={34} />
                <strong>拖拽文件到此处，或点击选择</strong>
                <small>支持 PDF、Markdown、Word、TXT 等格式</small>
                <input type="file" onChange={(event) => onFileChange(event.target.files?.[0] ?? null)} />
              </label>
            )}
          </div>
        ) : (
          <label className="upload-field">
            <span>远程 URL</span>
            <input value={sourceUrl} onChange={(event) => onSourceUrlChange(event.target.value)} placeholder="https://example.com/file.pdf" />
          </label>
        )}

        <section className="upload-chunk-panel">
          <label className="upload-field">
            <span>处理模式</span>
            <select value="chunk" disabled>
              <option value="chunk">直接分块</option>
            </select>
          </label>
          <label className="upload-field">
            <span>切分方式</span>
            <select value={chunkStrategy} onChange={(event) => onChunkStrategyChange(event.target.value)}>
              <option value="fixed_size">固定大小</option>
              <option value="structure_aware">结构感知</option>
            </select>
          </label>
          <div className="upload-chunk-grid">
            <label className="upload-field">
              <span>块大小</span>
              <input
                type="number"
                min={-1}
                value={chunkValues.chunkSize}
                onChange={(event) => updateChunkConfig({ chunkSize: Number(event.target.value) })}
              />
            </label>
            <label className="upload-field">
              <span>重叠大小</span>
              <input
                type="number"
                min={0}
                value={chunkValues.overlapSize}
                disabled={noChunk}
                onChange={(event) => updateChunkConfig({ overlapSize: Number(event.target.value) })}
              />
            </label>
            <label className="upload-no-chunk">
              <span>不分块</span>
              <input
                type="checkbox"
                checked={noChunk}
                onChange={(event) => updateChunkConfig({ chunkSize: event.target.checked ? -1 : 512 })}
              />
              <small>开启后块大小为 -1</small>
            </label>
          </div>
        </section>

        {sourceType === "url" ? (
          <section className="upload-schedule-panel">
            <label className="toggle">
              <input checked={scheduleEnabled} onChange={(event) => onScheduleEnabledChange(event.target.checked)} type="checkbox" />
              定时拉取 URL 文档
            </label>
            {scheduleEnabled ? (
              <label className="upload-field">
                <span>定时表达式</span>
                <input value={scheduleCron} onChange={(event) => onScheduleCronChange(event.target.value)} />
              </label>
            ) : null}
          </section>
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
