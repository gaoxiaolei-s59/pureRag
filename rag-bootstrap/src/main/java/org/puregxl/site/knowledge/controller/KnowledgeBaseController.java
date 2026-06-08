package org.puregxl.site.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseCreateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBasePageRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseUpdateRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeBaseInfoResponse;
import org.puregxl.site.knowledge.dto.response.KnowledgeBaseResponse;
import org.puregxl.site.knowledge.service.KnowledgeBaseService;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建对应的知识库
     * @param request
     * @return
     */
    @PostMapping("/knowledge-base")
    public Result<Void> createKnowledgeBase(@RequestBody @Valid KnowledgeBaseCreateRequest request) {
        knowledgeBaseService.createKnowledgeBase(request);
        return Results.success();
    }

    /**
     * 修改知识库
     * @param KbId
     * @param request
     * @return
     */
    @PutMapping("/knowledge-base/{kb-id}")
    public Result<Void> renameKnowledgeBase(@PathVariable("kb-id") String KbId,
                                            @RequestBody KnowledgeBaseUpdateRequest request) {
        knowledgeBaseService.renameKnowledgeBase(KbId, request);
        return Results.success();
    }


    /**
     * 删除知识库
     */
    @DeleteMapping("/knowledge-base/{kb-id}")
    public Result<Void> deleteKnowledgeBase(@PathVariable("kb-id") String kbId) {
        knowledgeBaseService.delete(kbId);
        return Results.success();
    }

    /**
     * 查询知识库内容
     */
    @GetMapping("/knowledge-base/{kb-id}")
    public Result<KnowledgeBaseResponse> queryKnowledgeBaseById(@PathVariable("kb-id") String kbId) {
        KnowledgeBaseResponse knowledgeBaseResponse = knowledgeBaseService.queryKnowledgeBaseById(kbId);
        return Results.success(knowledgeBaseResponse);
    }


    /**
     * 分页查询知识库列表
     */
    @GetMapping("/knowledge-base")
    public Result<IPage<KnowledgeBaseResponse>> pageQuery(KnowledgeBasePageRequest requestParam) {
        return Results.success(knowledgeBaseService.pageQuery(requestParam));
    }

    /**
     * 获取模型列表
     */
    @GetMapping("/knowledge/models")
    public Result<List<String>> queryModels(){
        return Results.success(knowledgeBaseService.queryModels());
    }


    /**
     * 查询所有的知识库的id和name用于意向图的创建
     * @return
     */
    @GetMapping("/knowledge/all")
    public Result<List<KnowledgeBaseInfoResponse>> queryAllKnowledgeBase() {
        return Results.success(knowledgeBaseService.queryAllKnowledgeBase());
    }

}
