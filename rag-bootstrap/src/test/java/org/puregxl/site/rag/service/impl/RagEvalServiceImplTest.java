package org.puregxl.site.rag.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.RetrievalContext;
import org.puregxl.site.rag.core.rewrite.RewriteResult;
import org.puregxl.site.rag.dto.req.RagPipelineEvalRequest;
import org.puregxl.site.rag.dto.resp.RagPipelineEvalResponse;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.pipeline.ChatPipeLine;
import org.puregxl.site.rag.pipeline.PipelineEvalResult;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvalServiceImplTest {

    @AfterEach
    void tearDown() {
        UserContext.removeUserContext();
    }

    @Test
    void evaluatePipelineMapsPipelineSnapshotToResponse() {
        ChatPipeLine chatPipeLine = mock(ChatPipeLine.class);
        RagEvalServiceImpl service = new RagEvalServiceImpl(chatPipeLine);
        RagPipelineEvalRequest request = new RagPipelineEvalRequest();
        request.setUserQuestion("报销流程在哪申请");
        request.setConversationId("conv-1");
        request.setDeepThinking(Boolean.TRUE);
        UserContext.setUserContext(UserInfoDTO.builder().userId("user-1").build());

        PipelineEvalResult pipelineEvalResult = PipelineEvalResult.builder()
                .userQuestion("报销流程在哪申请")
                .conversationId("conv-1")
                .deepThinking(true)
                .history(List.of(ChatMessage.user("之前问过报销")))
                .rewriteResult(RewriteResult.builder()
                        .rewrittenQuestion("报销流程在哪申请")
                        .subQuestions(List.of("报销流程在哪申请"))
                        .build())
                .subIntents(List.of(new SubQuestionIntent("报销流程在哪申请", List.of(
                        NodeScore.builder()
                                .score(0.92D)
                                .intentNode(IntentNode.builder()
                                        .id("intent-finance")
                                        .name("财务报销")
                                        .kind(IntentKind.KB)
                                        .kbId("kb-finance")
                                        .collectionName("kb_collection_finance")
                                        .build())
                                .build()
                ))))
                .allSystemOnly(false)
                .retrievalContext(RetrievalContext.builder()
                        .kbContext("子问题：报销流程在哪申请")
                        .mcpContext("")
                        .retrievedChunks(List.of(
                                RetrievedChunk.builder()
                                        .id("chunk-1")
                                        .docId("doc-1")
                                        .text("报销在 OA 系统发起")
                                        .score(0.95F)
                                        .build()
                        ))
                        .build())
                .rewriteLatencyMs(12L)
                .intentLatencyMs(23L)
                .retrievalLatencyMs(34L)
                .totalLatencyMs(69L)
                .build();
        when(chatPipeLine.evaluatePipeline("报销流程在哪申请", "conv-1", true, "user-1"))
                .thenReturn(pipelineEvalResult);

        RagPipelineEvalResponse response = service.evaluatePipeline(request);

        assertThat(response.getUserQuestion()).isEqualTo("报销流程在哪申请");
        assertThat(response.getPredictedIntentIds()).containsExactly("intent-finance");
        assertThat(response.getRetrievedChunkIds()).containsExactly("chunk-1");
        assertThat(response.getRetrievedDocIds()).containsExactly("doc-1");
        assertThat(response.getRetrievedChunks()).hasSize(1);
        assertThat(response.getSubQuestions()).hasSize(1);
        assertThat(response.getHistoryMessages()).hasSize(1);
        assertThat(response.getRewriteLatencyMs()).isEqualTo(12L);
        assertThat(response.getIntentLatencyMs()).isEqualTo(23L);
        assertThat(response.getRetrievalLatencyMs()).isEqualTo(34L);
        assertThat(response.getTotalLatencyMs()).isEqualTo(69L);
        verify(chatPipeLine).evaluatePipeline("报销流程在哪申请", "conv-1", true, "user-1");
    }
}
