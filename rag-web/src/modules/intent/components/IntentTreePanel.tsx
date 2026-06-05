import { ChevronDown } from "lucide-react";
import { ReactNode } from "react";
import { EmptyState } from "../../../components/common/EmptyState";
import { IntentTreeNode } from "../types";
import { getIntentCrudId } from "../utils";

type IntentTreePanelProps = {
  intentTree: IntentTreeNode[];
  selectedIntentId: string;
  intentSearch: string;
  intentLevelFilter: string;
  intentKindFilter: string;
  onSearchChange: (value: string) => void;
  onLevelFilterChange: (value: string) => void;
  onKindFilterChange: (value: string) => void;
  onSelectIntent: (recordId: string) => void;
};

function renderIntentTree(
  items: IntentTreeNode[],
  selectedIntentId: string,
  onSelectIntent: (recordId: string) => void,
  depth = 0
): ReactNode {
  if (!items.length) {
    return null;
  }
  return items.map((item) => (
    <div key={getIntentCrudId(item)} className="intent-tree-node">
      <button
        type="button"
        className={`intent-tree-item ${selectedIntentId === getIntentCrudId(item) ? "active" : ""}`}
        style={{ marginLeft: `${depth * 20}px` }}
        onClick={() => onSelectIntent(getIntentCrudId(item))}
      >
        <span className="intent-tree-leading">
          {item.treeChildren.length ? <ChevronDown size={15} /> : <span className="intent-tree-dot" />}
        </span>
        <span className="intent-tree-main">
          <strong>{item.name}</strong>
          <small>
            {item.level} / {item.kind}
          </small>
        </span>
      </button>
      {renderIntentTree(item.treeChildren, selectedIntentId, onSelectIntent, depth + 1)}
    </div>
  ));
}

export function IntentTreePanel({
  intentTree,
  selectedIntentId,
  intentSearch,
  intentLevelFilter,
  intentKindFilter,
  onSearchChange,
  onLevelFilterChange,
  onKindFilterChange,
  onSelectIntent
}: IntentTreePanelProps) {
  return (
    <section className="data-card">
      <div className="page-actions secondary">
        <input value={intentSearch} onChange={(event) => onSearchChange(event.target.value)} placeholder="筛选意图节点" />
        <select value={intentLevelFilter} onChange={(event) => onLevelFilterChange(event.target.value)}>
          <option value="all">全部层级</option>
          <option value="DOMAIN">DOMAIN</option>
          <option value="CATEGORY">CATEGORY</option>
          <option value="TOPIC">TOPIC</option>
        </select>
        <select value={intentKindFilter} onChange={(event) => onKindFilterChange(event.target.value)}>
          <option value="all">全部类型</option>
          <option value="KB">KB</option>
          <option value="MCP">MCP</option>
          <option value="SYSTEM">SYSTEM</option>
        </select>
      </div>
      <div className="intent-tree">
        {intentTree.length ? renderIntentTree(intentTree, selectedIntentId, onSelectIntent) : <EmptyState title="暂无节点" description="可以先创建根节点。" />}
      </div>
    </section>
  );
}
