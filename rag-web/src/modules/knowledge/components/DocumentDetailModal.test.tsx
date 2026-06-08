import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { DocumentDetailModal } from "./DocumentDetailModal";

describe("DocumentDetailModal", () => {
  it("should render document settings without concrete chunk content", () => {
    const html = renderToStaticMarkup(
      <DocumentDetailModal
        open
        loading={false}
        docDetail={{
          id: "doc-1",
          kbId: "kb-1",
          docName: "Java面试题介绍.docx",
          sourceType: "file",
          sourceLocation: "Java面试题介绍.docx",
          enabled: 1,
          chunkStrategy: "fixed_size",
          chunkConfig: JSON.stringify({ chunkSize: 512, overlapSize: 128 })
        }}
        docFormName="Java面试题介绍.docx"
        docFormEnabled
        docFormScheduleEnabled={false}
        docFormScheduleCron=""
        docFormChunkStrategy="fixed_size"
        docFormChunkConfig={JSON.stringify({ chunkSize: 512, overlapSize: 128 })}
        onClose={vi.fn()}
        onSubmitDocument={vi.fn()}
        onDocFormNameChange={vi.fn()}
        onDocFormEnabledChange={vi.fn()}
        onDocFormScheduleEnabledChange={vi.fn()}
        onDocFormScheduleCronChange={vi.fn()}
        onDocFormChunkStrategyChange={vi.fn()}
        onDocFormChunkConfigChange={vi.fn()}
      />
    );

    expect(html).toContain("编辑文档");
    expect(html).toContain("修改文档配置");
    expect(html).toContain("处理模式");
    expect(html).toContain("分块策略");
    expect(html).toContain("块大小");
    expect(html).toContain("重叠大小");
    expect(html).toContain("不分块");
    expect(html).not.toContain("Chunk 列表");
    expect(html).not.toContain("新增 Chunk");
    expect(html).not.toContain("SHOULD_NOT_RENDER_CHUNK_CONTENT");
  });
});
