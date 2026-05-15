package org.puregxl.site.bootstrap.knowledge.controller;

import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeBaseCreateRequest;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeBaseService;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
