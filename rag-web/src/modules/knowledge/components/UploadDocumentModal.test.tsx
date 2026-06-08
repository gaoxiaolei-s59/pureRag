import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { UploadDocumentModal } from "./UploadDocumentModal";

describe("UploadDocumentModal", () => {
  it("should render a prominent file drop area and chunk settings panel", () => {
    const html = renderToStaticMarkup(
      <UploadDocumentModal
        open
        loading={false}
        kbDetail={{ id: "kb-1", name: "产品知识库", embeddingModel: "qwen", collectionName: "kb_product" }}
        sourceType="file"
        sourceUrl=""
        scheduleEnabled={false}
        scheduleCron=""
        chunkStrategy="fixed_size"
        chunkConfig={JSON.stringify({ chunkSize: 512, overlapSize: 128 })}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        onSourceTypeChange={vi.fn()}
        onSourceUrlChange={vi.fn()}
        onFileChange={vi.fn()}
        onScheduleEnabledChange={vi.fn()}
        onScheduleCronChange={vi.fn()}
        onChunkStrategyChange={vi.fn()}
        onChunkConfigChange={vi.fn()}
      />
    );

    expect(html).toContain("upload-dropzone");
    expect(html).toContain("拖拽文件到此处，或点击选择");
    expect(html).toContain("upload-chunk-panel");
    expect(html).toContain("处理模式");
    expect(html).toContain("块大小");
    expect(html).toContain("重叠大小");
    expect(html).toContain("不分块");
  });
});
