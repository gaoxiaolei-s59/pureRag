package org.puregxl.site.rag.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.framework.convention.Result;
import org.puregxl.site.framework.web.Results;
import org.puregxl.site.rag.dto.req.RagPipelineEvalRequest;
import org.puregxl.site.rag.dto.resp.RagPipelineEvalResponse;
import org.puregxl.site.rag.service.RagEvalService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评测专用控制器。
 * 只复用 ChatPipeLine 生成前的链路，供离线评测脚本批量调用。
 */
@RestController
@RequiredArgsConstructor
public class RagEvalController {

    private final RagEvalService ragEvalService;

    /**
     * 评测旁路入口：
     * 不走 SSE，也不触发最终生成，只返回改写/意图/检索的结构化结果。
     */
    @PostMapping("/rag/eval/v1/pipeline")
    public Result<RagPipelineEvalResponse> evaluatePipeline(@RequestBody @Valid RagPipelineEvalRequest request) {
        return Results.success(ragEvalService.evaluatePipeline(request));
    }
}
