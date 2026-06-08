import {
  AlertTriangle,
  Database,
  FileText,
  FolderOpen,
  Pencil,
  Plus,
  RefreshCw,
  Trash2
} from "lucide-react";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { EmptyState } from "../../../components/common/EmptyState";
import { Modal } from "../../../components/common/Modal";
import { StatCard } from "../../../components/common/StatCard";
import { createKnowledgeBase, deleteKnowledgeBase, fetchKnowledgeBases, updateKnowledgeBase } from "../services/knowledge";
import { KnowledgeBase } from "../types";
import { formatDateTime } from "../utils";

export function KnowledgePage() {
  const navigate = useNavigate();
  const [alertMessage, setAlertMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [search, setSearch] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [selectedKb, setSelectedKb] = useState<KnowledgeBase | null>(null);
  const [newKbName, setNewKbName] = useState("");
  const [newKbCollection, setNewKbCollection] = useState("");
  const [newKbModel, setNewKbModel] = useState("qwen-emb-8b");
  const [editKbName, setEditKbName] = useState("");

  const filteredBases = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    if (!keyword) {
      return bases;
    }
    return bases.filter((item) =>
      `${item.name} ${item.collectionName} ${item.embeddingModel}`.toLowerCase().includes(keyword)
    );
  }, [bases, search]);

  async function refreshBases() {
    setLoading(true);
    try {
      const page = await fetchKnowledgeBases();
      setBases(page.records ?? []);
    } catch (error) {
      setAlertMessage(error instanceof Error ? error.message : "刷新知识库失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refreshBases();
  }, []);

  async function handleCreateKb(event: FormEvent) {
    event.preventDefault();
    if (!newKbName || !newKbCollection || !newKbModel) {
      setAlertMessage("请填写知识库名称、Collection 和模型");
      return;
    }
    setLoading(true);
    try {
      await createKnowledgeBase({
        name: newKbName,
        collectionName: newKbCollection,
        embeddingModel: newKbModel
      });
      setNewKbName("");
      setNewKbCollection("");
      setCreateOpen(false);
      await refreshBases();
    } catch (error) {
      setAlertMessage(error instanceof Error ? error.message : "创建知识库失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleUpdateKb(event: FormEvent) {
    event.preventDefault();
    if (!selectedKb || !editKbName.trim()) {
      setAlertMessage("请填写知识库名称");
      return;
    }
    setLoading(true);
    try {
      await updateKnowledgeBase(selectedKb.id, { name: editKbName.trim() });
      setEditOpen(false);
      await refreshBases();
    } catch (error) {
      setAlertMessage(error instanceof Error ? error.message : "更新知识库失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleDeleteKb(kb: KnowledgeBase) {
    setLoading(true);
    try {
      await deleteKnowledgeBase(kb.id);
      await refreshBases();
    } catch (error) {
      setAlertMessage(error instanceof Error ? error.message : "删除知识库失败");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page-shell">
      <div className="page-head">
        <div>
          <span className="eyebrow">Knowledge Bases</span>
          <h2>知识库管理</h2>
        </div>
        <div className="page-actions">
          <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索知识库名称" />
          <button type="button" className="outline-button" onClick={() => void refreshBases()}>
            <RefreshCw size={16} />
            刷新
          </button>
          <button type="button" className="gradient-button" onClick={() => setCreateOpen(true)}>
            <Plus size={16} />
            新建知识库
          </button>
        </div>
      </div>

      <section className="stats-grid">
        <StatCard icon={<Database size={20} />} label="知识库" value={bases.length} />
        <StatCard icon={<FileText size={20} />} label="文档数" value={bases.reduce((sum, item) => sum + (item.documentCount ?? 0), 0)} />
        <StatCard icon={<FolderOpen size={20} />} label="含文档知识库" value={bases.filter((item) => (item.documentCount ?? 0) > 0).length} />
      </section>

      <section className="data-card">
        <div className="knowledge-table-scroll">
          <div className="table-grid knowledge-grid table-header">
            <span>名称</span>
            <span>Embedding 模型</span>
            <span>Collection</span>
            <span>文档数</span>
            <span>创建时间</span>
            <span>更新时间</span>
            <span>操作</span>
          </div>
          {filteredBases.length ? (
            filteredBases.map((kb) => (
              <div className="table-grid knowledge-grid table-row" key={kb.id}>
                <span className="cell-strong" title={kb.name}>{kb.name}</span>
                <span className="cell-muted" title={kb.embeddingModel}>{kb.embeddingModel}</span>
                <span className="cell-code" title={kb.collectionName}>{kb.collectionName}</span>
                <span>{kb.documentCount ?? 0}</span>
                <span>{formatDateTime(kb.createTime)}</span>
                <span>{formatDateTime(kb.updateTime)}</span>
                <span className="row-actions">
                  <button type="button" className="outline-button small" onClick={() => navigate(`/knowledge/${kb.id}/docs`)}>
                    文档
                  </button>
                  <button
                    type="button"
                    className="outline-button small"
                    onClick={() => {
                      setSelectedKb(kb);
                      setEditKbName(kb.name);
                      setEditOpen(true);
                    }}
                  >
                    <Pencil size={14} />
                    编辑
                  </button>
                  <button type="button" className="outline-button small danger-text" onClick={() => void handleDeleteKb(kb)}>
                    <Trash2 size={14} />
                    删除
                  </button>
                </span>
              </div>
            ))
          ) : (
            <EmptyState title="暂无知识库" description="可以先创建一个知识库开始整理文档。" />
          )}
        </div>
      </section>

      {createOpen ? (
        <Modal title="创建知识库" description="创建新的文档容器和向量 collection" onClose={() => setCreateOpen(false)}>
          <form className="modal-form" onSubmit={handleCreateKb}>
            <label>
              <span>知识库名称</span>
              <input value={newKbName} onChange={(event) => setNewKbName(event.target.value)} />
            </label>
            <label>
              <span>Embedding 模型</span>
              <select value={newKbModel} onChange={(event) => setNewKbModel(event.target.value)}>
                <option value="qwen-emb-8b">qwen-emb-8b</option>
                <option value="qwen3-local">qwen3-local</option>
                <option value="text-embedding">text-embedding</option>
              </select>
            </label>
            <label>
              <span>Collection 名称</span>
              <input value={newKbCollection} onChange={(event) => setNewKbCollection(event.target.value)} />
            </label>
            <div className="modal-actions">
              <button type="button" className="outline-button" onClick={() => setCreateOpen(false)}>
                取消
              </button>
              <button type="submit" className="gradient-button" disabled={loading}>
                创建
              </button>
            </div>
          </form>
        </Modal>
      ) : null}

      {alertMessage ? (
        <Modal title="操作提示" description={alertMessage} onClose={() => setAlertMessage(null)}>
          <div className="alert-modal-body" aria-hidden="true">
            <AlertTriangle size={34} />
          </div>
          <div className="modal-actions">
            <button type="button" className="gradient-button" onClick={() => setAlertMessage(null)}>
              知道了
            </button>
          </div>
        </Modal>
      ) : null}

      {editOpen && selectedKb ? (
        <Modal title="编辑知识库" description={`修改「${selectedKb.name}」的展示信息`} onClose={() => setEditOpen(false)}>
          <form className="modal-form" onSubmit={handleUpdateKb}>
            <label>
              <span>知识库名称</span>
              <input value={editKbName} onChange={(event) => setEditKbName(event.target.value)} />
            </label>
            <label>
              <span>Embedding 模型</span>
              <input value={selectedKb.embeddingModel} disabled />
            </label>
            <label>
              <span>Collection 名称</span>
              <input value={selectedKb.collectionName} disabled />
            </label>
            <div className="modal-actions">
              <button type="button" className="outline-button" onClick={() => setEditOpen(false)}>
                取消
              </button>
              <button type="submit" className="gradient-button" disabled={loading}>
                保存
              </button>
            </div>
          </form>
        </Modal>
      ) : null}
    </div>
  );
}
