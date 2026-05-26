package org.puregxl.site.rag.pipeline;

import org.junit.jupiter.api.Test;
import org.puregxl.site.rag.config.RAGDefaultProperties;
import org.puregxl.site.rag.core.rewrite.QueryRewriteService;
import org.puregxl.site.rag.core.rewrite.RewriteResult;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.chat.StreamCallback;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.retrieval.RagRetrievalService;
import org.puregxl.site.rag.service.MemoryService;
import org.puregxl.site.rag.support.PromptTemplateLoader;

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
        LLMService llmService = mock(LLMService.class);
        MemoryService memoryService = mock(MemoryService.class);
        QueryRewriteService queryRewriteService = mock(QueryRewriteService.class);
        PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader();
        RAGDefaultProperties properties = new RAGDefaultProperties();
        properties.setCollectionName("kb_collection");
        properties.setRetrieveTopK(8);
        ChatPipeLine pipeLine = new ChatPipeLine(
                embeddingService,
                retrievalService,
                llmService,
                properties,
                memoryService,
                queryRewriteService,
                promptTemplateLoader);

        String question = "订单超时后会发生什么？";
        String rewrittenQuestion = "订单在超时未支付后会发生什么？";
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
        StreamCallback callback = mock(StreamCallback.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        RewriteResult rewriteResult = RewriteResult.builder()
                .rewrittenQuestion(rewrittenQuestion)
                .build();

        when(embeddingService.embed(rewrittenQuestion)).thenReturn(queryVector);
        when(memoryService.loadMemory(any(), any(), any(ChatMessage.class))).thenReturn(List.of());
        when(queryRewriteService.rewrite(question, List.of())).thenReturn(rewriteResult);
        when(retrievalService.searchSimilarChunks("kb_collection", queryVector, 8)).thenReturn(candidates);
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

    @Test
    void executeUsesLatestFourTurnsForRewriteAndStoresRewriteResult() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        LLMService llmService = mock(LLMService.class);
        MemoryService memoryService = mock(MemoryService.class);
        QueryRewriteService queryRewriteService = mock(QueryRewriteService.class);
        PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader();
        RAGDefaultProperties properties = new RAGDefaultProperties();
        properties.setCollectionName("kb_collection");
        properties.setRetrieveTopK(8);
        ChatPipeLine pipeLine = new ChatPipeLine(
                embeddingService,
                retrievalService,
                llmService,
                properties,
                memoryService,
                queryRewriteService,
                promptTemplateLoader);

        String question = "那它多久会自动取消？";
        String rewrittenQuestion = "订单多久未支付会自动取消？";
        RewriteResult rewriteResult = RewriteResult.builder()
                .rewrittenQuestion(rewrittenQuestion)
                .build();
        List<ChatMessage> history = List.of(
                ChatMessage.system("这是摘要"),
                ChatMessage.user("第1轮用户"),
                ChatMessage.assistant("第1轮助手"),
                ChatMessage.user("第2轮用户"),
                ChatMessage.assistant("第2轮助手"),
                ChatMessage.user("第3轮用户"),
                ChatMessage.assistant("第3轮助手"),
                ChatMessage.user("第4轮用户"),
                ChatMessage.assistant("第4轮助手"),
                ChatMessage.user("第5轮用户"),
                ChatMessage.assistant("第5轮助手")
        );
        List<ChatMessage> latestFourTurns = List.of(
                ChatMessage.user("第2轮用户"),
                ChatMessage.assistant("第2轮助手"),
                ChatMessage.user("第3轮用户"),
                ChatMessage.assistant("第3轮助手"),
                ChatMessage.user("第4轮用户"),
                ChatMessage.assistant("第4轮助手"),
                ChatMessage.user("第5轮用户"),
                ChatMessage.assistant("第5轮助手")
        );
        StreamCallback callback = mock(StreamCallback.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        StreamChatContext context = StreamChatContext.builder()
                .question(question)
                .conversationId("conv-1")
                .userId("user-1")
                .callback(callback)
                .build();

        when(memoryService.loadMemory(any(), any(), any(ChatMessage.class))).thenReturn(history);
        when(queryRewriteService.rewrite(question, latestFourTurns)).thenReturn(rewriteResult);
        when(embeddingService.embed(rewrittenQuestion)).thenReturn(List.of(0.1F, 0.2F));
        when(retrievalService.searchSimilarChunks("kb_collection", List.of(0.1F, 0.2F), 8)).thenReturn(List.of());
        when(llmService.streamChat(any(ChatRequest.class), org.mockito.ArgumentMatchers.eq(callback))).thenReturn(handle);

        pipeLine.execute(context);

        verify(queryRewriteService).rewrite(question, latestFourTurns);
        assertThat(context.getRewriteResult()).isSameAs(rewriteResult);
        verify(embeddingService).embed(rewrittenQuestion);
    }
}
