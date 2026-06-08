package org.puregxl.site.knowledge.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentPageRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentUpdateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeDocumentResponse;
import org.puregxl.site.knowledge.service.KnowledgeDocumentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDocumentController {
    private final KnowledgeDocumentService documentService;

    /**
     * 上传文档：入库记录 + 文件落盘，返回文档ID
     */
    @PostMapping(value = "/knowledge-base/{kb-id}/docs/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<KnowledgeDocumentResponse> uploadKnowledgeDocument(@PathVariable("kb-id") String kbId,
                                                                     @RequestPart(value = "file", required = false) MultipartFile file,
                                                                     @ModelAttribute KnowledgeDocumentUploadRequest requestParam) {
        return Results.success(documentService.uploadKnowledgeDocument(kbId, requestParam, file));
    }

    /**
     * 开始分块：抽取文本 -> 分块 -> 嵌入并写入向量库
     */
    @PostMapping("/knowledge-base/docs/{doc-id}/chunk")
    public Result<Void> startChunkKnowledgeDocument(@PathVariable(value = "doc-id") String docId) {
        documentService.startChunkKnowledgeDocument(docId);
        return Results.success();
    }


    /**
     * 删除文档：逻辑删除。可选同时删除向量库中该文档的所有 chunk
     */
    @DeleteMapping("/knowledge-base/docs/{doc-id}")
    public Result<Void> deleteKnowledgeDocument(@PathVariable(value = "doc-id") String docId) {
        documentService.deleteKnowledgeDocument(docId);
        return Results.success();
    }

    /**
     * 查询文档详情
     */
    @GetMapping("/knowledge-base/docs/{docId}")
    public Result<KnowledgeDocumentResponse> getKnowledgeDocument(@PathVariable String docId) {
        return Results.success(documentService.getKnowledgeDocument(docId));
    }


    /**
     * 更新文档信息
     */
    @PutMapping("/knowledge-base/docs/{docId}")
    public Result<Void> updateKnowledgeDocument(@PathVariable String docId,
                               @RequestBody KnowledgeDocumentUpdateRequest requestParam) {
        documentService.updateKnowledgeDocument(docId, requestParam);
        return Results.success();
    }

    /**
     * 分页查询文档列表（支持状态/关键字过滤）
     */
    @GetMapping("/knowledge-base/{kb-id}/docs")
    public Result<IPage<KnowledgeDocumentResponse>> pageKnowledgeDocument(@PathVariable(value = "kb-id") String kbId,
                                                                          KnowledgeDocumentPageRequest requestParam) {
        return Results.success(documentService.pageKnowledgeDocument(kbId, requestParam));
    }

}
