package org.puregxl.site.rag.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.RetrievalContext;
import org.puregxl.site.rag.core.rewrite.RewriteResult;

import java.util.List;

/**
 * ChatPipeLine 前置链路评测快照。
 * 只承载“生成前”的结构化结果，供评测旁路服务映射成对外响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineEvalResult {

    /**
     * 本轮用户原始问题（进入 pipeline 前已经 trim）。
     */
    private String userQuestion;

    /**
     * 当前会话 ID，可为空。
     */
    private String conversationId;

    /**
     * 本轮是否开启深度思考。
     */
    private boolean deepThinking;

    /**
     * 参与改写与最终生成的历史消息。
     */
    private List<ChatMessage> history;

    /**
     * 问题改写结果。
     */
    private RewriteResult rewriteResult;

    /**
     * 子问题意图识别结果。
     */
    private List<SubQuestionIntent> subIntents;

    /**
     * 是否全部命中 SYSTEM 意图。
     */
    private boolean allSystemOnly;

    /**
     * 非 SYSTEM 流程的检索结果快照；纯 SYSTEM 场景为 null。
     */
    private RetrievalContext retrievalContext;

    /**
     * 各阶段耗时，单位毫秒。
     */
    private long rewriteLatencyMs;
    private long intentLatencyMs;
    private long retrievalLatencyMs;
    private long totalLatencyMs;
}
