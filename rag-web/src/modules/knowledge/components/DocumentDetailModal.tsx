import { Loader2 } from "lucide-react";
import { FormEvent } from "react";
import { Modal } from "../../../components/common/Modal";
import { KnowledgeDocument } from "../types";

type DocumentDetailModalProps = {
  open: boolean;
  loading: boolean;
  docDetail: KnowledgeDocument | null;
  docFormName: string;
  docFormEnabled: boolean;
  docFormScheduleEnabled: boolean;
  docFormScheduleCron: string;
  docFormChunkStrategy: string;
  docFormChunkConfig: string;
  onClose: () => void;
  onSubmitDocument: (event: FormEvent) => void;
  onDocFormNameChange: (value: string) => void;
  onDocFormEnabledChange: (value: boolean) => void;
  onDocFormScheduleEnabledChange: (value: boolean) => void;
  onDocFormScheduleCronChange: (value: string) => void;
  onDocFormChunkStrategyChange: (value: string) => void;
  onDocFormChunkConfigChange: (value: string) => void;
};

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

export function DocumentDetailModal({
  open,
  loading,
  docDetail,
  docFormName,
  docFormEnabled,
  docFormScheduleEnabled,
  docFormScheduleCron,
  docFormChunkStrategy,
  docFormChunkConfig,
  onClose,
  onSubmitDocument,
  onDocFormNameChange,
  onDocFormEnabledChange,
  onDocFormScheduleEnabledChange,
  onDocFormScheduleCronChange,
  onDocFormChunkStrategyChange,
  onDocFormChunkConfigChange
}: DocumentDetailModalProps) {
  if (!open || !docDetail) {
    return null;
  }

  const chunkValues = readChunkConfig(docFormChunkConfig);
  const noChunk = chunkValues.chunkSize === -1;
  const isUrlSource = docDetail.sourceType === "url";
  const sourceLabel = isUrlSource ? "远程 URL" : "本地文件";
  const sourceTypeLabel = isUrlSource ? "Remote URL" : "Local File";

  function updateChunkConfig(next: Partial<{ chunkSize: number; overlapSize: number }>) {
    onDocFormChunkConfigChange(JSON.stringify({ ...chunkValues, ...next }, null, 2));
  }

  return (
    <Modal
      title="编辑文档"
      description="修改文档配置，保存后需重新分块才会生效"
      onClose={onClose}
      cardClassName="upload-modal-card"
    >
      <form className="modal-form upload-document-form" onSubmit={onSubmitDocument}>
        <label className="upload-field">
          <span>来源类型</span>
          <select value={docDetail.sourceType} disabled>
            <option value={docDetail.sourceType}>{sourceTypeLabel}</option>
          </select>
        </label>

        <label className="upload-field">
          <span>{sourceLabel}</span>
          <input value={docFormName} onChange={(event) => onDocFormNameChange(event.target.value)} />
        </label>

        <section className="upload-chunk-panel">
          <label className="upload-field">
            <span>处理模式</span>
            <select value="chunk" disabled>
              <option value="chunk">分块策略</option>
            </select>
          </label>
          <p className="field-helper">分块策略：直接分块；数据通道：使用Pipeline清洗</p>

          <section className="document-chunk-inner">
            <label className="upload-field">
              <span>分块策略</span>
              <select value={docFormChunkStrategy} onChange={(event) => onDocFormChunkStrategyChange(event.target.value)}>
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
                <small>字符数</small>
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
                <small>开启后块大小为-1</small>
              </label>
            </div>
          </section>
        </section>

        <section className="upload-schedule-panel">
          <label className="toggle">
            <input checked={docFormEnabled} onChange={(event) => onDocFormEnabledChange(event.target.checked)} type="checkbox" />
            启用文档
          </label>
          {isUrlSource ? (
            <>
              <label className="toggle">
                <input
                  checked={docFormScheduleEnabled}
                  onChange={(event) => onDocFormScheduleEnabledChange(event.target.checked)}
                  type="checkbox"
                />
                启用定时拉取
              </label>
              {docFormScheduleEnabled ? (
                <label className="upload-field">
                  <span>定时表达式</span>
                  <input value={docFormScheduleCron} onChange={(event) => onDocFormScheduleCronChange(event.target.value)} />
                </label>
              ) : null}
            </>
          ) : null}
        </section>

        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose}>
            关闭
          </button>
          <button type="submit" className="gradient-button" disabled={loading}>
            {loading ? <Loader2 className="spin" size={16} /> : null}
            保存
          </button>
        </div>
      </form>
    </Modal>
  );
}
