package org.puregxl.site.rag.service;

import org.puregxl.site.rag.dto.req.RagPipelineEvalRequest;
import org.puregxl.site.rag.dto.resp.RagPipelineEvalResponse;

/**
 * RAG 评测旁路服务。
 */
public interface RagEvalService {

    /**
     * 执行一次前置链路评测，不进入大模型生成。
     */
    RagPipelineEvalResponse evaluatePipeline(RagPipelineEvalRequest request);
}
