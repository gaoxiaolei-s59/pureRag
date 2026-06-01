import { FormEvent, useMemo, useState } from "react";
import { fetchKnowledgeBases } from "../../knowledge/services/knowledge";
import { KnowledgeBase } from "../../knowledge/types";
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
import { buildIntentTree } from "../utils";

export function useIntentTreePage() {
  const [notice, setNotice] = useState("意图树已拆成独立页面，可继续扩展路由和节点能力");
  const [loading, setLoading] = useState(false);
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
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
      const [basePage, records] = await Promise.all([fetchKnowledgeBases(), fetchIntentNodes()]);
      setBases(basePage.records ?? []);
      setIntentNodes(records ?? []);

      const fallbackId = records?.[0]?.recordId ?? "";
      const preferredId =
        nextSelectedIntentId && (records ?? []).some((item) => item.recordId === nextSelectedIntentId)
          ? nextSelectedIntentId
          : fallbackId;
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
    if (!selectedIntentDetail?.recordId) {
      return;
    }
    setLoading(true);
    try {
      await deleteIntentNode(selectedIntentDetail.recordId);
      setNotice(`已删除节点：${selectedIntentDetail.name}`);
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
      if (intentFormMode === "edit" && intentForm.recordId) {
        await updateIntentNode(intentForm.recordId, payload);
      } else {
        await createIntentNode(payload);
      }
      setIntentFormOpen(false);
      await refreshPage(intentForm.recordId);
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
