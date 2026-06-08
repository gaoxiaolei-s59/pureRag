import { FormEvent, useMemo, useState } from "react";
import { fetchAllKnowledgeBases } from "../../knowledge/services/knowledge";
import { KnowledgeBaseInfo } from "../../knowledge/types";
import {
  buildIntentNodePayload,
  createChildIntentForm,
  createEditIntentForm,
  createEmptyIntentForm,
  IntentFormMode,
  IntentFormState
} from "../form";
import { filterIntentNodes } from "../selectors";
import { createIntentNode, deleteIntentNode, fetchIntentNodeDetail, fetchIntentNodes, updateIntentNode } from "../services/intent";
import { IntentNode } from "../types";
import { buildIntentTree, getIntentCrudId, isSameIntentIdentity } from "../utils";

export function useIntentTreePage() {
  const [notice, setNotice] = useState("意图树已拆成独立页面，可继续扩展路由和节点能力");
  const [loading, setLoading] = useState(false);
  const [bases, setBases] = useState<KnowledgeBaseInfo[]>([]);
  const [intentNodes, setIntentNodes] = useState<IntentNode[]>([]);
  const [intentSearch, setIntentSearch] = useState("");
  const [intentLevelFilter, setIntentLevelFilter] = useState("all");
  const [intentKindFilter, setIntentKindFilter] = useState("all");
  const [selectedIntentId, setSelectedIntentId] = useState("");
  const [selectedIntentDetail, setSelectedIntentDetail] = useState<IntentNode | null>(null);
  const [intentFormOpen, setIntentFormOpen] = useState(false);
  const [intentFormMode, setIntentFormMode] = useState<IntentFormMode>("create-root");
  const [intentForm, setIntentForm] = useState<IntentFormState>(createEmptyIntentForm());

  const filteredIntentNodes = useMemo(
    () => filterIntentNodes(intentNodes, intentSearch, intentLevelFilter, intentKindFilter),
    [intentNodes, intentSearch, intentLevelFilter, intentKindFilter]
  );

  const intentTree = useMemo(() => buildIntentTree(filteredIntentNodes), [filteredIntentNodes]);

  /**
   * 刷新意图树页的主数据，并尽量保留当前选中节点。
   * 列表刷新后会补一次详情查询，确保右侧面板展示的是最新节点内容。
   */
  async function refreshPage(nextSelectedIntentId = selectedIntentId) {
    setLoading(true);
    try {
      const [bases, records] = await Promise.all([fetchAllKnowledgeBases(), fetchIntentNodes()]);
      setBases(bases ?? []);
      setIntentNodes(records ?? []);

      const fallbackId = records?.[0] ? getIntentCrudId(records[0]) : "";
      const preferredRecord = nextSelectedIntentId ? (records ?? []).find((item) => isSameIntentIdentity(item, nextSelectedIntentId)) : null;
      const preferredId = preferredRecord ? getIntentCrudId(preferredRecord) : fallbackId;
      setSelectedIntentId(preferredId);
      if (preferredId) {
        const detail = await fetchIntentNodeDetail(preferredId);
        setSelectedIntentDetail(detail);
      } else {
        setSelectedIntentDetail(null);
      }
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "刷新意图列表失败");
    } finally {
      setLoading(false);
    }
  }

  function openCreateRootIntent() {
    setIntentFormMode("create-root");
    setIntentForm(createEmptyIntentForm());
    setIntentFormOpen(true);
  }

  function openCreateChildIntent(parent: IntentNode) {
    setIntentFormMode("create-child");
    setIntentForm(createChildIntentForm(parent));
    setIntentFormOpen(true);
  }

  function openEditIntent(node: IntentNode) {
    setIntentFormMode("edit");
    setIntentForm(createEditIntentForm(node));
    setIntentFormOpen(true);
  }

  /**
   * 切换当前选中的意图节点，并刷新右侧详情面板。
   */
  async function handleSelectIntent(recordId: string) {
    if (!recordId) {
      setNotice("当前节点缺少可操作 ID，请刷新意图树后重试");
      return;
    }
    setSelectedIntentId(recordId);
    setLoading(true);
    try {
      const detail = await fetchIntentNodeDetail(recordId);
      setSelectedIntentDetail(detail);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "加载意图详情失败");
    } finally {
      setLoading(false);
    }
  }

  async function handleDeleteIntentNode() {
    const crudId = selectedIntentDetail ? getIntentCrudId(selectedIntentDetail) : "";
    if (!crudId) {
      setNotice("当前节点缺少可删除 ID，请刷新意图树后重试");
      return;
    }
    const deletingName = selectedIntentDetail?.name ?? crudId;
    setLoading(true);
    try {
      await deleteIntentNode(crudId);
      setNotice(`已删除节点：${deletingName}`);
      setSelectedIntentDetail(null);
      setSelectedIntentId("");
      await refreshPage();
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "删除意图节点失败");
    } finally {
      setLoading(false);
    }
  }

  /**
   * 提交意图节点创建/编辑表单。
   * 表单清洗和可选字段归一化交给纯函数处理，hook 只保留页面编排职责。
   */
  async function handleIntentFormSubmit(event: FormEvent) {
    event.preventDefault();
    if (!intentForm.name.trim() || !intentForm.intentCode.trim()) {
      setNotice("请填写节点名称和意图标识");
      return;
    }
    setLoading(true);
    try {
      const payload = buildIntentNodePayload(intentForm);
      if (intentFormMode === "edit") {
        const crudId = intentForm.recordId || (selectedIntentDetail ? getIntentCrudId(selectedIntentDetail) : "");
        if (!crudId) {
          setNotice("当前节点缺少可编辑 ID，请刷新意图树后重试");
          return;
        }
        await updateIntentNode(crudId, payload);
      } else {
        await createIntentNode(payload);
      }
      setIntentFormOpen(false);
      await refreshPage(intentForm.recordId || payload.intentCode);
    } catch (error) {
      setNotice(error instanceof Error ? error.message : "保存意图节点失败");
    } finally {
      setLoading(false);
    }
  }

  return {
    notice,
    loading,
    bases,
    intentNodes,
    intentTree,
    intentSearch,
    intentLevelFilter,
    intentKindFilter,
    selectedIntentId,
    selectedIntentDetail,
    intentFormOpen,
    intentFormMode,
    intentForm,
    refreshPage,
    openCreateRootIntent,
    openCreateChildIntent,
    openEditIntent,
    handleSelectIntent,
    handleDeleteIntentNode,
    handleIntentFormSubmit,
    setIntentSearch,
    setIntentLevelFilter,
    setIntentKindFilter,
    setIntentFormOpen,
    setIntentForm
  };
}
