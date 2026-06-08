import { Loader2, Plus } from "lucide-react";
import { FormEvent } from "react";
import { Modal } from "../../../components/common/Modal";
import { KnowledgeBaseInfo } from "../../knowledge/types";
import { IntentFormMode, IntentFormState } from "../form";
import { IntentNode } from "../types";

type IntentFormModalProps = {
  open: boolean;
  loading: boolean;
  mode: IntentFormMode;
  form: IntentFormState;
  intentNodes: IntentNode[];
  bases: KnowledgeBaseInfo[];
  onClose: () => void;
  onSubmit: (event: FormEvent) => void;
  onFormChange: (updater: (current: IntentFormState) => IntentFormState) => void;
};

export function IntentFormModal({
  open,
  loading,
  mode,
  form,
  intentNodes,
  bases,
  onClose,
  onSubmit,
  onFormChange
}: IntentFormModalProps) {
  if (!open) {
    return null;
  }

  return (
    <Modal
      title={mode === "edit" ? "编辑意图节点" : "新建意图节点"}
      description="配置节点的层级、类型与知识库关联"
      onClose={onClose}
    >
      <form className="modal-form" onSubmit={onSubmit}>
        <div className="two-column-form">
          <label>
            <span>节点名称</span>
            <input value={form.name} onChange={(event) => onFormChange((current) => ({ ...current, name: event.target.value }))} />
          </label>
          <label>
            <span>意图标识</span>
            <input
              value={form.intentCode}
              onChange={(event) => onFormChange((current) => ({ ...current, intentCode: event.target.value }))}
            />
          </label>
        </div>
        <div className="two-column-form">
          <label>
            <span>层级</span>
            <select value={form.level} onChange={(event) => onFormChange((current) => ({ ...current, level: Number(event.target.value) }))}>
              <option value={0}>DOMAIN</option>
              <option value={1}>CATEGORY</option>
              <option value={2}>TOPIC</option>
            </select>
          </label>
          <label>
            <span>类型</span>
            <select value={form.kind} onChange={(event) => onFormChange((current) => ({ ...current, kind: Number(event.target.value) }))}>
              <option value={0}>KB</option>
              <option value={2}>SYSTEM</option>
              <option value={1}>MCP</option>
            </select>
          </label>
        </div>
        <label>
          <span>父节点</span>
          <select value={form.parentCode} onChange={(event) => onFormChange((current) => ({ ...current, parentCode: event.target.value }))}>
            <option value="">ROOT</option>
            {intentNodes
              .filter((item) => !form.recordId || item.recordId !== form.recordId)
              .map((item) => (
                <option key={item.recordId} value={item.id}>
                  {item.name} ({item.id})
                </option>
              ))}
          </select>
        </label>
        <label>
          <span>知识库（可选）</span>
          <select value={form.kbId} onChange={(event) => onFormChange((current) => ({ ...current, kbId: event.target.value }))}>
            <option value="">请选择知识库</option>
            {bases.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>描述</span>
          <textarea
            value={form.description}
            onChange={(event) => onFormChange((current) => ({ ...current, description: event.target.value }))}
            rows={4}
          />
        </label>
        <label>
          <span>示例问题</span>
          <textarea
            value={form.examplesText}
            onChange={(event) => onFormChange((current) => ({ ...current, examplesText: event.target.value }))}
            rows={4}
          />
        </label>
        <div className="two-column-form">
          <label>
            <span>Collection</span>
            <input
              value={form.collectionName}
              onChange={(event) => onFormChange((current) => ({ ...current, collectionName: event.target.value }))}
            />
          </label>
          <label>
            <span>MCP 工具 ID</span>
            <input
              value={form.mcpToolId}
              onChange={(event) => onFormChange((current) => ({ ...current, mcpToolId: event.target.value }))}
            />
          </label>
        </div>
        <div className="two-column-form">
          <label>
            <span>TopK</span>
            <input value={form.topK} onChange={(event) => onFormChange((current) => ({ ...current, topK: event.target.value }))} />
          </label>
          <label>
            <span>排序</span>
            <input
              value={form.sortOrder}
              onChange={(event) => onFormChange((current) => ({ ...current, sortOrder: event.target.value }))}
            />
          </label>
        </div>
        <label className="toggle">
          <input
            checked={form.enabled}
            onChange={(event) => onFormChange((current) => ({ ...current, enabled: event.target.checked }))}
            type="checkbox"
          />
          启用节点
        </label>
        <label>
          <span>Prompt 片段</span>
          <textarea
            value={form.promptSnippet}
            onChange={(event) => onFormChange((current) => ({ ...current, promptSnippet: event.target.value }))}
            rows={3}
          />
        </label>
        <label>
          <span>Prompt 模板</span>
          <textarea
            value={form.promptTemplate}
            onChange={(event) => onFormChange((current) => ({ ...current, promptTemplate: event.target.value }))}
            rows={3}
          />
        </label>
        <label>
          <span>参数提取模板</span>
          <textarea
            value={form.paramPromptTemplate}
            onChange={(event) => onFormChange((current) => ({ ...current, paramPromptTemplate: event.target.value }))}
            rows={3}
          />
        </label>
        <div className="modal-actions">
          <button type="button" className="outline-button" onClick={onClose}>
            取消
          </button>
          <button type="submit" className="gradient-button" disabled={loading}>
            {loading ? <Loader2 className="spin" size={16} /> : <Plus size={16} />}
            {mode === "edit" ? "保存" : "创建"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
