import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { KnowledgeDocumentsPage } from "./KnowledgeDocumentsPage";

vi.mock("../hooks/useKnowledgeDocumentsPage", () => ({
  useKnowledgeDocumentsPage: () => ({
    kbId: "kb-1",
    notice: "文档已上传",
    loading: false,
    kbDetail: {
      id: "kb-1",
      name: "产品文档库",
      embeddingModel: "qwen",
      collectionName: "pro"
    },
    documents: [],
    filteredDocuments: [
      {
        id: "doc-1",
        kbId: "kb-1",
        docName: "1.Java面试题介绍.docx",
        sourceType: "file",
        sourceLocation: "Local File",
        enabled: 1,
        chunkCount: 0,
        fileSize: 638976,
        chunkStrategy: "fixed_size",
        status: "pending",
        updatedBy: "admin",
        updateTime: "2026-06-08T15:00:00"
      }
    ],
    selectedDocDetail: null,
    search: "",
    statusFilter: "all",
    uploadOpen: false,
    docDetailOpen: false,
    sourceType: "file",
    sourceUrl: "",
    file: null,
    scheduleEnabled: false,
    scheduleCron: "",
    chunkStrategy: "fixed_size",
    chunkConfig: "{}",
    docFormName: "",
    docFormEnabled: true,
    docFormScheduleEnabled: false,
    docFormScheduleCron: "",
    docFormChunkStrategy: "fixed_size",
    docFormChunkConfig: "{}",
    refreshPage: vi.fn(),
    openDocumentDetail: vi.fn(),
    setSearch: vi.fn(),
    setStatusFilter: vi.fn(),
    setUploadOpen: vi.fn(),
    setDocDetailOpen: vi.fn(),
    setSourceType: vi.fn(),
    setSourceUrl: vi.fn(),
    setFile: vi.fn(),
    setScheduleEnabled: vi.fn(),
    setScheduleCron: vi.fn(),
    setChunkStrategy: vi.fn(),
    setChunkConfig: vi.fn(),
    setDocFormName: vi.fn(),
    setDocFormEnabled: vi.fn(),
    setDocFormScheduleEnabled: vi.fn(),
    setDocFormScheduleCron: vi.fn(),
    setDocFormChunkStrategy: vi.fn(),
    setDocFormChunkConfig: vi.fn(),
    handleUpload: vi.fn(),
    handleChunk: vi.fn(),
    handleDeleteDoc: vi.fn(),
    handleUpdateDocument: vi.fn()
  })
}));

describe("KnowledgeDocumentsPage", () => {
  it("should render the polished document management card layout", () => {
    const html = renderToStaticMarkup(
      <MemoryRouter>
        <KnowledgeDocumentsPage />
      </MemoryRouter>
    );

    expect(html).toContain("首页");
    expect(html).toContain("文档管理");
    expect(html).toContain("产品文档库（pro）");
    expect(html).toContain("文档列表");
    expect(html).toContain("支持筛选与分块管理");
    expect(html).toContain("docs-card-toolbar");
    expect(html).toContain("搜索文档名称");
    expect(html).toContain("全部状态");
    expect(html).toContain("处理模式");
    expect(html).toContain("Chunk");
    expect(html).not.toContain('class="notice-text"');
  });
});
