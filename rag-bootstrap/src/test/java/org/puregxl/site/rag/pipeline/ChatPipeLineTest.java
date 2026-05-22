package org.puregxl.site.rag.pipeline;

import org.junit.jupiter.api.Test;
import org.puregxl.site.rag.config.RAGDefaultProperties;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.chat.StreamCallback;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.infra.rerank.RerankService;
import org.puregxl.site.rag.retrieval.RagRetrievalService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPipeLineTest {

    @Test
    void executeRetrievesRelevantChunksAndStreamsRagPrompt() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RerankService rerankService = mock(RerankService.class);
        LLMService llmService = mock(LLMService.class);
        RAGDefaultProperties properties = new RAGDefaultProperties();
        properties.setCollectionName("kb_collection");
        properties.setRetrieveTopK(8);
        properties.setRerankTopN(4);
        ChatPipeLine pipeLine = new ChatPipeLine(
                embeddingService,
                retrievalService,
                rerankService,
                llmService,
                properties);

        String question = "订单超时后会发生什么？";
        List<Float> queryVector = List.of(0.1F, 0.2F);
        List<RetrievedChunk> candidates = List.of(
                RetrievedChunk.builder()
                        .id("chunk-1")
                        .text("订单超过 30 分钟未支付时，系统会自动关闭订单。")
                        .score(0.92F)
                        .build(),
                RetrievedChunk.builder()
                        .id("chunk-2")
                        .text("已支付订单进入履约流程。")
                        .score(0.61F)
                        .build());
        List<RetrievedChunk> reranked = List.of(candidates.get(0));
        StreamCallback callback = mock(StreamCallback.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        when(embeddingService.embed(question)).thenReturn(queryVector);
        when(retrievalService.searchSimilarChunks("kb_collection", queryVector, 8)).thenReturn(candidates);
        when(rerankService.rerank(question, candidates, 4)).thenReturn(reranked);
        when(llmService.streamChat(any(ChatRequest.class), org.mockito.ArgumentMatchers.eq(callback))).thenReturn(handle);

        StreamCancellationHandle actual = pipeLine.execute(StreamChatContext.builder()
                .question(question)
                .deepThinking(true)
                .callback(callback)
                .build());

        assertThat(actual).isSameAs(handle);
        var requestCaptor = org.mockito.ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), org.mockito.ArgumentMatchers.eq(callback));
        ChatRequest request = requestCaptor.getValue();
        assertThat(request.getThinking()).isTrue();
        assertThat(request.getTemperature()).isEqualTo(0.2D);
        assertThat(request.getMessages()).hasSize(2);
        assertThat(request.getMessages().get(0).getRole()).isEqualTo(ChatMessage.Role.SYSTEM);
        assertThat(request.getMessages().get(0).getContent())
                .contains("订单超过 30 分钟未支付时，系统会自动关闭订单。")
                .contains("[1]");
        assertThat(request.getMessages().get(1).getRole()).isEqualTo(ChatMessage.Role.USER);
        assertThat(request.getMessages().get(1).getContent()).isEqualTo(question);
    }
}
