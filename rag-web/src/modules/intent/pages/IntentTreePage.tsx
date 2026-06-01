import {
  ClipboardList,
  Layers3,
  Plus,
  RefreshCw
} from "lucide-react";
import { useEffect } from "react";
import { StatCard } from "../../../components/common/StatCard";
import { IntentDetailPanel } from "../components/IntentDetailPanel";
import { IntentFormModal } from "../components/IntentFormModal";
import { IntentTreePanel } from "../components/IntentTreePanel";
import { useIntentTreePage } from "../hooks/useIntentTreePage";

export function IntentTreePage() {
  const page = useIntentTreePage();

  useEffect(() => {
    void page.refreshPage();
  }, []);

  return (
    <div className="page-shell intent-page">
      <div className="page-head">
        <div>
          <span className="eyebrow">Intent Tree</span>
          <h2>意图树配置</h2>
        </div>
        <div className="page-actions">
          <button type="button" className="outline-button" onClick={() => void page.refreshPage()}>
            <RefreshCw size={16} />
            刷新
          </button>
          <button type="button" className="gradient-button" onClick={page.openCreateRootIntent}>
            <Plus size={16} />
            新建根节点
          </button>
        </div>
      </div>

      <p className="notice-text">{page.notice}</p>

      <section className="stats-grid">
        <StatCard icon={<Layers3 size={20} />} label="节点总数" value={page.intentNodes.length} />
        <StatCard icon={<ClipboardList size={20} />} label="启用节点" value={page.intentNodes.filter((item) => item.enabled === 1).length} />
        <StatCard icon={<Plus size={20} />} label="根节点" value={page.intentNodes.filter((item) => !item.parentId).length} />
      </section>

      <div className="intent-layout">
        <IntentTreePanel
          intentTree={page.intentTree}
          selectedIntentId={page.selectedIntentId}
          intentSearch={page.intentSearch}
          intentLevelFilter={page.intentLevelFilter}
          intentKindFilter={page.intentKindFilter}
          onSearchChange={page.setIntentSearch}
          onLevelFilterChange={page.setIntentLevelFilter}
          onKindFilterChange={page.setIntentKindFilter}
          onSelectIntent={(recordId) => void page.handleSelectIntent(recordId)}
        />

        <IntentDetailPanel
          bases={page.bases}
          selectedIntentDetail={page.selectedIntentDetail}
          onCreateChild={page.openCreateChildIntent}
          onEdit={page.openEditIntent}
          onDelete={() => void page.handleDeleteIntentNode()}
        />
      </div>

      <IntentFormModal
        open={page.intentFormOpen}
        loading={page.loading}
        mode={page.intentFormMode}
        form={page.intentForm}
        intentNodes={page.intentNodes}
        bases={page.bases}
        onClose={() => page.setIntentFormOpen(false)}
        onSubmit={(event) => void page.handleIntentFormSubmit(event)}
        onFormChange={(updater) => page.setIntentForm(updater)}
      />
    </div>
  );
}
