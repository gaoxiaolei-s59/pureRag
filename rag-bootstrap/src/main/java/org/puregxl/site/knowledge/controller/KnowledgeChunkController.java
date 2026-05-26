package org.puregxl.site.knowledge.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.knowledge.dto.request.KnowledgeChunkBatchRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeChunkCreateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeChunkUpdateRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeChunkResponse;
import org.puregxl.site.knowledge.service.KnowledgeChunkService;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.springframework.web.bind.annotation.*;

/**
 * 知识库 Chunk 管理接口
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class KnowledgeChunkController {

    private final KnowledgeChunkService knowledgeChunkService;

    /**
     * 查询文档下 Chunk 列表
     */
    @GetMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Result<java.util.List<KnowledgeChunkResponse>> listKnowledgeChunks(@PathVariable("doc-id") String docId) {
        return Results.success(knowledgeChunkService.listKnowledgeChunks(docId));
    }

    /**
     * 新增 Chunk
     */
    @PostMapping("/knowledge-base/docs/{doc-id}/chunks")
    public Result<KnowledgeChunkResponse> createKnowledgeChunk(@PathVariable("doc-id") String docId,
                                                 @RequestBody KnowledgeChunkCreateRequest request) {
        return Results.success(knowledgeChunkService.createKnowledgeChunk(docId, request));
    }

    /**
     * 更新 Chunk 内容
     */
    @PutMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Result<Void> updateKnowledgeChunk(@PathVariable("doc-id") String docId,
                                             @PathVariable("chunk-id") String chunkID,
                                             @RequestBody KnowledgeChunkUpdateRequest request) {
        knowledgeChunkService.updateKnowledgeChunk(docId, chunkID, request);
        return Results.success();
    }

    /**
     * 删除 Chunk
     */
    @DeleteMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}")
    public Result<Void> deleteKnowledgeChunk(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId) {
        knowledgeChunkService.delete(docId, chunkId);
        return Results.success();
    }

    /**
     * 启用或禁用单条 Chunk
     */
    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/{chunk-id}/enable")
    public Result<Void> enable(@PathVariable("doc-id") String docId,
                               @PathVariable("chunk-id") String chunkId,
                               @RequestParam("value") boolean enabled) {
        knowledgeChunkService.enableChunk(docId, chunkId, enabled);
        return Results.success();
    }


    /**
     * 批量启用或禁用 Chunk
     */
    @PatchMapping("/knowledge-base/docs/{doc-id}/chunks/batch-enable")
    public Result<Void> batchEnable(@PathVariable("doc-id") String docId,
                                    @RequestParam("value") boolean enabled,
                                    @RequestBody(required = false) KnowledgeChunkBatchRequest request) {
        knowledgeChunkService.batchToggleEnabled(docId, request, enabled);
        return Results.success();
    }

}
