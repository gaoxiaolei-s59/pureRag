package org.puregxl.site.rag.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.RetrievalContext;
import org.puregxl.site.rag.core.rewrite.RewriteResult;
import org.puregxl.site.rag.dto.req.RagPipelineEvalRequest;
import org.puregxl.site.rag.dto.resp.RagPipelineEvalResponse;
import org.puregxl.site.rag.pipeline.ChatPipeLine;
import org.puregxl.site.rag.pipeline.PipelineEvalResult;
import org.puregxl.site.rag.service.RagEvalService;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 评测旁路实现：
 * 读取正式用户上下文，复用 ChatPipeLine 的前置步骤，并把内部快照映射为稳定的外部响应。
 */
@Service
@RequiredArgsConstructor
public class RagEvalServiceImpl implements RagEvalService {

    private final ChatPipeLine chatPipeLine;

    @Override
    public RagPipelineEvalResponse evaluatePipeline(RagPipelineEvalRequest request) {
        PipelineEvalResult result = chatPipeLine.evaluatePipeline(
                request.getUserQuestion(),
                request.getConversationId(),
                Boolean.TRUE.equals(request.getDeepThinking()),
                currentUserId()
        );

        RewriteResult rewriteResult = result.getRewriteResult();
        List<SubQuestionIntent> subIntents = Optional.ofNullable(result.getSubIntents()).orElse(List.of());
        RetrievalContext retrievalContext = result.getRetrievalContext();
        List<RetrievedChunk> retrievedChunks = retrievalContext == null || retrievalContext.getRetrievedChunks() == null
                ? List.of()
                : retrievalContext.getRetrievedChunks();

        return RagPipelineEvalResponse.builder()
                .userQuestion(result.getUserQuestion())
                .conversationId(result.getConversationId())
                .deepThinking(result.isDeepThinking())
                .historyMessages(mapHistory(result.getHistory()))
                .rewrittenQuestion(rewriteResult == null ? null : rewriteResult.getRewrittenQuestion())
                .rewrittenSubQuestions(rewriteResult == null || rewriteResult.getSubQuestions() == null
                        ? List.of()
                        : rewriteResult.getSubQuestions())
                .subQuestions(mapSubQuestions(subIntents))
                .predictedIntentIds(mapPredictedIntentIds(subIntents))
                .allSystemOnly(result.isAllSystemOnly())
                .hasKb(retrievalContext != null && retrievalContext.hasKb())
                .hasMcp(retrievalContext != null && retrievalContext.hasMcp())
                .retrievedChunkIds(retrievedChunks.stream()
                        .map(RetrievedChunk::getId)
                        .filter(StrUtil::isNotBlank)
                        .toList())
                .retrievedDocIds(new LinkedHashSet<>(retrievedChunks.stream()
                        .map(RetrievedChunk::getDocId)
                        .filter(StrUtil::isNotBlank)
                        .toList()).stream().toList())
                .retrievedChunks(mapRetrievedChunks(retrievedChunks))
                .kbContext(retrievalContext == null ? "" : retrievalContext.getKbContext())
                .mcpContext(retrievalContext == null ? "" : retrievalContext.getMcpContext())
                .rewriteLatencyMs(result.getRewriteLatencyMs())
                .intentLatencyMs(result.getIntentLatencyMs())
                .retrievalLatencyMs(result.getRetrievalLatencyMs())
                .totalLatencyMs(result.getTotalLatencyMs())
                .build();
    }

    private List<RagPipelineEvalResponse.HistoryMessage> mapHistory(List<ChatMessage> history) {
        if (CollUtil.isEmpty(history)) {
            return List.of();
        }
        return history.stream()
                .filter(Objects::nonNull)
                .map(message -> RagPipelineEvalResponse.HistoryMessage.builder()
                        .role(message.getRole() == null ? null : message.getRole().name())
                        .content(message.getContent())
                        .build())
                .toList();
    }

    private List<RagPipelineEvalResponse.SubQuestionEvalResult> mapSubQuestions(List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents)) {
            return List.of();
        }
        return subIntents.stream()
                .filter(Objects::nonNull)
                .map(subIntent -> RagPipelineEvalResponse.SubQuestionEvalResult.builder()
                        .subQuestion(subIntent.getSubQuestion())
                        .intentCandidates(mapIntentCandidates(subIntent.getNodeScores()))
                        .build())
                .toList();
    }

    private List<RagPipelineEvalResponse.IntentCandidate> mapIntentCandidates(List<NodeScore> nodeScores) {
        if (CollUtil.isEmpty(nodeScores)) {
            return List.of();
        }
        return nodeScores.stream()
                .filter(Objects::nonNull)
                .filter(nodeScore -> nodeScore.getIntentNode() != null)
                .map(nodeScore -> RagPipelineEvalResponse.IntentCandidate.builder()
                        .id(nodeScore.getIntentNode().getId())
                        .name(nodeScore.getIntentNode().getName())
                        .kind(nodeScore.getIntentNode().getKind())
                        .kbId(nodeScore.getIntentNode().getKbId())
                        .collectionName(nodeScore.getIntentNode().getCollectionName())
                        .mcpToolId(nodeScore.getIntentNode().getMcpToolId())
                        .score(nodeScore.getScore())
                        .build())
                .toList();
    }

    private List<String> mapPredictedIntentIds(List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents)) {
            return List.of();
        }
        return subIntents.stream()
                .filter(Objects::nonNull)
                .map(SubQuestionIntent::getNodeScores)
                .filter(CollUtil::isNotEmpty)
                .map(scores -> scores.get(0))
                .filter(Objects::nonNull)
                .map(NodeScore::getIntentNode)
                .filter(Objects::nonNull)
                .map(intentNode -> intentNode.getId())
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private List<RagPipelineEvalResponse.RetrievedChunkResult> mapRetrievedChunks(List<RetrievedChunk> retrievedChunks) {
        if (CollUtil.isEmpty(retrievedChunks)) {
            return List.of();
        }
        return retrievedChunks.stream()
                .filter(Objects::nonNull)
                .map(chunk -> RagPipelineEvalResponse.RetrievedChunkResult.builder()
                        .chunkId(chunk.getId())
                        .docId(chunk.getDocId())
                        .text(chunk.getText())
                        .score(chunk.getScore())
                        .build())
                .toList();
    }

    private String currentUserId() {
        UserInfoDTO userInfo = UserContext.getUserContext();
        return userInfo == null ? null : userInfo.getUserId();
    }
}
