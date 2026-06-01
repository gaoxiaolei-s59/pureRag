import { Trash2 } from "lucide-react";
import { EmptyState } from "../../../components/common/EmptyState";
import { KnowledgeBase } from "../../knowledge/types";
import { IntentNode } from "../types";
import { intentKindText, intentLevelText } from "../utils";

type IntentDetailPanelProps = {
  bases: KnowledgeBase[];
  selectedIntentDetail: IntentNode | null;
  onCreateChild: (node: IntentNode) => void;
  onEdit: (node: IntentNode) => void;
  onDelete: () => void;
};

export function IntentDetailPanel({
  bases,
  selectedIntentDetail,
  onCreateChild,
  onEdit,
  onDelete
}: IntentDetailPanelProps) {
  return (
    <section className="data-card intent-detail-card">
      {selectedIntentDetail ? (
        <>
          <div className="intent-detail-head">
            <div>
              <span className="eyebrow">Intent Detail</span>
              <h3>{selectedIntentDetail.name}</h3>
            </div>
            <div className="row-actions">
              <button type="button" className="outline-button small" onClick={() => onCreateChild(selectedIntentDetail)}>
                子节点
              </button>
              <button type="button" className="outline-button small" onClick={() => onEdit(selectedIntentDetail)}>
                编辑
              </button>
              <button type="button" className="outline-button small danger-text" onClick={onDelete}>
                <Trash2 size={14} />
                删除
              </button>
            </div>
          </div>

          <div className="intent-fields">
            <div className="intent-field-row">
              <span>意图标识</span>
              <strong>{selectedIntentDetail.id}</strong>
            </div>
            <div className="intent-field-row">
              <span>层级</span>
              <strong>{intentLevelText(selectedIntentDetail.level)}</strong>
            </div>
            <div className="intent-field-row">
              <span>类型</span>
              <strong>{intentKindText(selectedIntentDetail.kind)}</strong>
            </div>
            <div className="intent-field-row">
              <span>知识库</span>
              <strong>{bases.find((item) => item.id === selectedIntentDetail.kbId)?.name || "-"}</strong>
            </div>
            <div className="intent-field-row">
              <span>Collection</span>
              <strong>{selectedIntentDetail.collectionName || "-"}</strong>
            </div>
            <div className="intent-field-row">
              <span>描述</span>
              <strong>{selectedIntentDetail.description || "-"}</strong>
            </div>
          </div>
        </>
      ) : (
        <EmptyState title="请选择节点" description="左侧点击节点后，这里会展示详细配置。" />
      )}
    </section>
  );
}
