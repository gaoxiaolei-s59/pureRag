package org.puregxl.site.rag.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.puregxl.site.rag.enums.IntentKind;

import java.util.List;

/**
 * ChatPipeLine 前置链路评测响应。
 * 该结构面向 Python 评测脚本，返回改写、意图、检索和上下文拼装的结构化结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagPipelineEvalResponse {

    private String userQuestion;

    private String conversationId;

    private Boolean deepThinking;

    private List<HistoryMessage> historyMessages;

    private String rewrittenQuestion;

    private List<String> rewrittenSubQuestions;

    private List<SubQuestionEvalResult> subQuestions;

    private List<String> predictedIntentIds;

    private boolean allSystemOnly;

    private boolean hasKb;

    private boolean hasMcp;

    private List<String> retrievedChunkIds;

    private List<String> retrievedDocIds;

    private List<RetrievedChunkResult> retrievedChunks;

    private String kbContext;

    private String mcpContext;

    private long rewriteLatencyMs;

    private long intentLatencyMs;

    private long retrievalLatencyMs;

    private long totalLatencyMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryMessage {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubQuestionEvalResult {
        private String subQuestion;
        private List<IntentCandidate> intentCandidates;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntentCandidate {
        private String id;
        private String name;
        private IntentKind kind;
        private String kbId;
        private String collectionName;
        private String mcpToolId;
        private double score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievedChunkResult {
        private String chunkId;
        private String docId;
        private String text;
        private Float score;
    }
}
