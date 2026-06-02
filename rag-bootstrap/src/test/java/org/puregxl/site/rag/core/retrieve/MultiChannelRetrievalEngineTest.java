package org.puregxl.site.rag.core.retrieve;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.config.RAGDefaultProperties;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.channel.DefaultSearchChannelProcessor;
import org.puregxl.site.rag.core.retrieve.channel.RerankSearchChannelProcess;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannel;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannelResult;
import org.puregxl.site.rag.enums.SearchChannelType;
import org.puregxl.site.infra.rerank.RerankService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiChannelRetrievalEngineTest {

    @Test
    void searchExecutesEnabledChannelsByPriorityDeduplicatesAndReranks() {
        SearchChannel intentChannel = mock(SearchChannel.class);
        SearchChannel globalChannel = mock(SearchChannel.class);
        SearchChannel disabledChannel = mock(SearchChannel.class);
        RerankService rerankService = mock(RerankService.class);
        RAGDefaultProperties ragDefaultProperties = mock(RAGDefaultProperties.class);
        MultiChannelRetrievalEngine retrievalEngine = new MultiChannelRetrievalEngine(
                List.of(globalChannel, disabledChannel, intentChannel),
                List.of(new DefaultSearchChannelProcessor(), new RerankSearchChannelProcess(rerankService, ragDefaultProperties)),
                Runnable::run
        );
        SubQuestionIntent subIntent = new SubQuestionIntent("OA 怎么申请", List.of());
        RetrievedChunk intentChunk = RetrievedChunk.builder()
                .id("chunk-1")
                .text("OA 申请需要先提交审批")
                .score(0.93F)
                .build();
        RetrievedChunk globalDuplicate = RetrievedChunk.builder()
                .id("chunk-1")
                .text("全局重复结果不应该覆盖意图结果")
                .score(0.99F)
                .build();
        RetrievedChunk globalChunk = RetrievedChunk.builder()
                .id("chunk-2")
                .text("OA 申请也支持移动端提交")
                .score(0.81F)
                .build();

        when(intentChannel.getName()).thenReturn("intent");
        when(globalChannel.getName()).thenReturn("global");
        when(disabledChannel.getName()).thenReturn("disabled");
        when(intentChannel.priority()).thenReturn(10);
        when(globalChannel.priority()).thenReturn(100);
        when(disabledChannel.priority()).thenReturn(1);
        when(intentChannel.isEnabled(subIntent)).thenReturn(true);
        when(globalChannel.isEnabled(subIntent)).thenReturn(true);
        when(disabledChannel.isEnabled(subIntent)).thenReturn(false);
        when(intentChannel.search(subIntent, 2)).thenReturn(SearchChannelResult.builder()
                .searchChannelType(SearchChannelType.VECTOR)
                .channelName("intent")
                .retrievedChunks(List.of(intentChunk))
                .intentChunks(Map.of("intent-oa", List.of(intentChunk)))
                .build());
        when(globalChannel.search(subIntent, 2)).thenReturn(SearchChannelResult.builder()
                .searchChannelType(SearchChannelType.GLOBAL)
                .channelName("global")
                .retrievedChunks(List.of(globalDuplicate, globalChunk))
                .intentChunks(Map.of())
                .build());
        when(rerankService.rerank(eq("OA 怎么申请"), eq(List.of(intentChunk, globalChunk)), eq(2)))
                .thenReturn(List.of(globalChunk, intentChunk));

        List<RetrievedChunk> result = retrievalEngine.search(subIntent, 2);

        assertThat(result).containsExactly(globalChunk, intentChunk);
        verify(disabledChannel).isEnabled(subIntent);
        verify(rerankService).rerank("OA 怎么申请", List.of(intentChunk, globalChunk), 2);
    }

    @Test
    void searchIgnoresFailedChannelAndKeepsSuccessfulResults() {
        SearchChannel failedChannel = mock(SearchChannel.class);
        SearchChannel successChannel = mock(SearchChannel.class);
        MultiChannelRetrievalEngine retrievalEngine = new MultiChannelRetrievalEngine(
                List.of(failedChannel, successChannel),
                List.of(new DefaultSearchChannelProcessor()),
                Runnable::run
        );
        SubQuestionIntent subIntent = new SubQuestionIntent("报销制度", List.of());
        RetrievedChunk chunk = RetrievedChunk.builder().id("chunk-ok").text("报销需要发票").score(0.8F).build();

        when(failedChannel.getName()).thenReturn("failed");
        when(successChannel.getName()).thenReturn("success");
        when(failedChannel.priority()).thenReturn(1);
        when(successChannel.priority()).thenReturn(2);
        when(failedChannel.isEnabled(subIntent)).thenReturn(true);
        when(successChannel.isEnabled(subIntent)).thenReturn(true);
        when(failedChannel.search(subIntent, 3)).thenThrow(new IllegalStateException("remote timeout"));
        when(successChannel.search(subIntent, 3)).thenReturn(SearchChannelResult.builder()
                .searchChannelType(SearchChannelType.GLOBAL)
                .channelName("success")
                .retrievedChunks(List.of(chunk))
                .intentChunks(Map.of())
                .build());

        List<RetrievedChunk> result = retrievalEngine.search(subIntent, 3);

        assertThat(result).containsExactly(chunk);
    }
}
