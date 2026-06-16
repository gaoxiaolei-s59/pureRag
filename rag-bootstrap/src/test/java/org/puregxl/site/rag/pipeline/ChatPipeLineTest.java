package org.puregxl.site.rag.pipeline;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.chat.StreamCallback;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.config.SearchChannelProperties;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.IntentResolver;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.RetrievalContext;
import org.puregxl.site.rag.core.retrieve.RetrievalEngine;
import org.puregxl.site.rag.core.rewrite.QueryRewriteService;
import org.puregxl.site.rag.core.rewrite.RewriteResult;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.service.MemoryService;
import org.puregxl.site.rag.support.PromptTemplateLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPipeLineTest {

    @Test
    void evaluatePipelineCollectsRewriteIntentAndRetrievalSignalsWithoutInvokingLlm() {
        LLMService llmService = mock(LLMService.class);
        MemoryService memoryService = mock(MemoryService.class);
        QueryRewriteService queryRewriteService = mock(QueryRewriteService.class);
        IntentResolver intentResolver = mock(IntentResolver.class);
        RetrievalEngine retrievalEngine = mock(RetrievalEngine.class);
        PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader();
        SearchChannelProperties searchChannelProperties = new SearchChannelProperties();
        searchChannelProperties.setDefaultTopK(6);
        ChatPipeLine pipeLine = new ChatPipeLine(
                llmService,
                memoryService,
                queryRewriteService,
                promptTemplateLoader,
                intentResolver,
                retrievalEngine,
                searchChannelProperties
        );

        String question = "报销流程在哪申请";
        RewriteResult rewriteResult = RewriteResult.builder()
                .rewrittenQuestion("报销流程在哪申请")
                .subQuestions(List.of("报销流程在哪申请", "报销材料要上传到哪里"))
                .build();
        List<ChatMessage> history = List.of(
                ChatMessage.user("之前问过报销"),
                ChatMessage.assistant("你可以继续问我流程问题")
        );
        List<SubQuestionIntent> subIntents = List.of(
                new SubQuestionIntent("报销流程在哪申请", List.of(
                        NodeScore.builder()
                                .score(0.91D)
                                .intentNode(IntentNode.builder()
                                        .id("intent-finance")
                                        .name("财务报销")
                                        .kind(IntentKind.KB)
                                        .kbId("kb-finance")
                                        .collectionName("kb_collection_finance")
                                        .build())
                                .build()
                ))
        );
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("""
                        子问题：报销流程在哪申请
                        知识库检索结果：
                        1. 报销在 OA 系统发起""")
                .mcpContext("MCP工具结果：当前没有额外工具结果")
                .retrievedChunks(List.of(
                        RetrievedChunk.builder()
                                .id("chunk-1")
                                .docId("doc-1")
                                .text("报销在 OA 系统发起")
                                .score(0.95F)
                                .build()
                ))
                .build();

        when(memoryService.loadMemory(any(), any(), any(ChatMessage.class))).thenReturn(history);
        when(queryRewriteService.rewrite(question, history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(intentResolver.isSystemOnly(subIntents.get(0).getNodeScores())).thenReturn(false);
        when(retrievalEngine.retrieval(subIntents, 6, "user-1")).thenReturn(retrievalContext);

        PipelineEvalResult actual = pipeLine.evaluatePipeline(question, "conv-1", false, "user-1");

        assertThat(actual.getUserQuestion()).isEqualTo(question);
        assertThat(actual.getConversationId()).isEqualTo("conv-1");
        assertThat(actual.getHistory()).containsExactlyElementsOf(history);
        assertThat(actual.getRewriteResult()).isSameAs(rewriteResult);
        assertThat(actual.getSubIntents()).containsExactlyElementsOf(subIntents);
        assertThat(actual.isAllSystemOnly()).isFalse();
        assertThat(actual.getRetrievalContext()).isSameAs(retrievalContext);
        assertThat(actual.getRetrievalContext().getRetrievedChunks())
                .extracting(RetrievedChunk::getId, RetrievedChunk::getDocId)
                .containsExactly(tuple("chunk-1", "doc-1"));
        assertThat(actual.getRewriteLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(actual.getIntentLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(actual.getRetrievalLatencyMs()).isGreaterThanOrEqualTo(0L);
        assertThat(actual.getTotalLatencyMs()).isGreaterThanOrEqualTo(actual.getRewriteLatencyMs());
        verify(retrievalEngine).retrieval(subIntents, 6, "user-1");
        verify(llmService, never()).streamChat(any(ChatRequest.class), any(StreamCallback.class));
    }

    @Test
    void evaluatePipelineSkipsRetrievalWhenAllSubQuestionsAreSystemOnly() {
        LLMService llmService = mock(LLMService.class);
        MemoryService memoryService = mock(MemoryService.class);
        QueryRewriteService queryRewriteService = mock(QueryRewriteService.class);
        IntentResolver intentResolver = mock(IntentResolver.class);
        RetrievalEngine retrievalEngine = mock(RetrievalEngine.class);
        PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader();
        SearchChannelProperties searchChannelProperties = new SearchChannelProperties();
        ChatPipeLine pipeLine = new ChatPipeLine(
                llmService,
                memoryService,
                queryRewriteService,
                promptTemplateLoader,
                intentResolver,
                retrievalEngine,
                searchChannelProperties
        );

        String question = "帮我写一段自我介绍";
        RewriteResult rewriteResult = RewriteResult.builder()
                .rewrittenQuestion(question)
                .subQuestions(List.of(question))
                .build();
        List<SubQuestionIntent> subIntents = List.of(
                new SubQuestionIntent(question, List.of(
                        NodeScore.builder()
                                .score(0.95D)
                                .intentNode(IntentNode.builder().id("system-intent").kind(IntentKind.SYSTEM).build())
                                .build()
                ))
        );

        when(memoryService.loadMemory(any(), any(), any(ChatMessage.class))).thenReturn(List.of());
        when(queryRewriteService.rewrite(question, List.of())).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(intentResolver.isSystemOnly(subIntents.get(0).getNodeScores())).thenReturn(true);

        PipelineEvalResult actual = pipeLine.evaluatePipeline(question, null, false, "user-1");

        assertThat(actual.isAllSystemOnly()).isTrue();
        assertThat(actual.getRetrievalContext()).isNull();
        assertThat(actual.getRetrievalLatencyMs()).isZero();
        verify(retrievalEngine, never()).retrieval(any(), any(Integer.class), any());
        verify(llmService, never()).streamChat(any(ChatRequest.class), any(StreamCallback.class));
    }

    @Test
    void executeShortCircuitsToDirectChatWhenAllSubQuestionsAreSystemOnly() {
        LLMService llmService = mock(LLMService.class);
        MemoryService memoryService = mock(MemoryService.class);
        QueryRewriteService queryRewriteService = mock(QueryRewriteService.class);
        IntentResolver intentResolver = mock(IntentResolver.class);
        RetrievalEngine retrievalEngine = mock(RetrievalEngine.class);
        PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader();
        SearchChannelProperties searchChannelProperties = new SearchChannelProperties();
        ChatPipeLine pipeLine = new ChatPipeLine(
                llmService,
                memoryService,
                queryRewriteService,
                promptTemplateLoader,
                intentResolver,
                retrievalEngine,
                searchChannelProperties
        );

        String question = "帮我写一段自我介绍";
        StreamCallback callback = mock(StreamCallback.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        RewriteResult rewriteResult = RewriteResult.builder()
                .rewrittenQuestion(question)
                .subQuestions(List.of(question))
                .build();
        List<ChatMessage> history = List.of(
                ChatMessage.user("你是谁？"),
                ChatMessage.assistant("我是助手。")
        );
        List<SubQuestionIntent> subIntents = List.of(
                new SubQuestionIntent(question, List.of(
                        NodeScore.builder()
                                .score(0.95D)
                                .intentNode(IntentNode.builder().id("system-intent").kind(IntentKind.SYSTEM).build())
                                .build()
                ))
        );

        when(memoryService.loadMemory(any(), any(), any(ChatMessage.class))).thenReturn(history);
        when(queryRewriteService.rewrite(question, history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(intentResolver.isSystemOnly(subIntents.get(0).getNodeScores())).thenReturn(true);
        when(llmService.streamChat(any(ChatRequest.class), eq(callback))).thenReturn(handle);

        StreamCancellationHandle actual = pipeLine.execute(StreamChatContext.builder()
                .question(question)
                .conversationId("conv-1")
                .userId("user-1")
                .deepThinking(true)
                .callback(callback)
                .build());

        assertThat(actual).isSameAs(handle);
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), eq(callback));
        ChatRequest request = requestCaptor.getValue();
        assertThat(request.getThinking()).isTrue();
        assertThat(request.getMessages()).containsExactly(
                ChatMessage.user("你是谁？"),
                ChatMessage.assistant("我是助手。"),
                ChatMessage.user(question)
        );
        verify(retrievalEngine, never()).retrieval(any(), any(Integer.class), any());
    }

    @Test
    void executeUsesRetrievalContextFromMultiChannelEngineForNonSystemIntent() {
        LLMService llmService = mock(LLMService.class);
        MemoryService memoryService = mock(MemoryService.class);
        QueryRewriteService queryRewriteService = mock(QueryRewriteService.class);
        IntentResolver intentResolver = mock(IntentResolver.class);
        RetrievalEngine retrievalEngine = mock(RetrievalEngine.class);
        PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader();
        SearchChannelProperties searchChannelProperties = new SearchChannelProperties();
        searchChannelProperties.setDefaultTopK(6);
        ChatPipeLine pipeLine = new ChatPipeLine(
                llmService,
                memoryService,
                queryRewriteService,
                promptTemplateLoader,
                intentResolver,
                retrievalEngine,
                searchChannelProperties
        );

        String question = "报销流程在哪申请";
        StreamCallback callback = mock(StreamCallback.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        RewriteResult rewriteResult = RewriteResult.builder()
                .rewrittenQuestion(question)
                .subQuestions(List.of(question))
                .build();
        List<ChatMessage> history = List.of(ChatMessage.user("之前问过报销"));
        List<SubQuestionIntent> subIntents = List.of(
                new SubQuestionIntent(question, List.of(
                        NodeScore.builder()
                                .score(0.88D)
                                .intentNode(IntentNode.builder()
                                        .id("intent-finance")
                                        .kind(IntentKind.KB)
                                        .kbId("kb-finance")
                                        .collectionName("kb_collection_finance")
                                        .build())
                                .build()
                ))
        );
        RetrievalContext retrievalContext = RetrievalContext.builder()
                .kbContext("""
                        子问题：报销流程在哪申请
                        知识库检索结果：
                        1. 报销在 OA 系统发起""")
                .mcpContext("MCP工具结果：当前没有额外工具结果")
                .build();

        when(memoryService.loadMemory(any(), any(), any(ChatMessage.class))).thenReturn(history);
        when(queryRewriteService.rewrite(question, history)).thenReturn(rewriteResult);
        when(intentResolver.resolve(rewriteResult)).thenReturn(subIntents);
        when(intentResolver.isSystemOnly(subIntents.get(0).getNodeScores())).thenReturn(false);
        when(retrievalEngine.retrieval(subIntents, 6, "user-1")).thenReturn(retrievalContext);
        when(llmService.streamChat(any(ChatRequest.class), eq(callback))).thenReturn(handle);

        StreamCancellationHandle actual = pipeLine.execute(StreamChatContext.builder()
                .question(question)
                .conversationId("conv-1")
                .userId("user-1")
                .callback(callback)
                .build());

        assertThat(actual).isSameAs(handle);
        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(llmService).streamChat(requestCaptor.capture(), eq(callback));
        String systemPrompt = requestCaptor.getValue().getMessages().get(0).getContent();
        assertThat(systemPrompt).contains("子问题：报销流程在哪申请");
        assertThat(systemPrompt).contains("知识库检索结果：");
        assertThat(systemPrompt).contains("报销在 OA 系统发起");
        assertThat(systemPrompt).contains("MCP工具结果：当前没有额外工具结果");
        verify(retrievalEngine).retrieval(subIntents, 6, "user-1");
    }
}
