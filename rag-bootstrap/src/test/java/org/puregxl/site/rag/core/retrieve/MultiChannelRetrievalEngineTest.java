package org.puregxl.site.rag.core.retrieve;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannel;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannelResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiChannelRetrievalEngineTest {

    @Test
    void retrieveKbByIntentMergesChunksFromMultipleChannels() {
        SearchChannel firstChannel = mock(SearchChannel.class);
        SearchChannel secondChannel = mock(SearchChannel.class);
        MultiChannelRetrievalEngine retrievalEngine = new MultiChannelRetrievalEngine(List.of(firstChannel, secondChannel));
        SubQuestionIntent subIntent = new SubQuestionIntent("OA 怎么申请", List.of());

        when(firstChannel.priority()).thenReturn(10);
        when(secondChannel.priority()).thenReturn(100);
        when(firstChannel.isEnabled(subIntent)).thenReturn(true);
        when(secondChannel.isEnabled(subIntent)).thenReturn(true);
        when(firstChannel.search(subIntent, 5)).thenReturn(SearchChannelResult.builder()
                .retrievedChunks(List.of(
                        RetrievedChunk.builder().id("chunk-1").text("OA 申请需要先提交审批").score(0.93F).build()
                ))
                .intentChunks(Map.of(
                        "intent-oa", List.of(RetrievedChunk.builder().id("chunk-1").text("OA 申请需要先提交审批").score(0.93F).build())
                ))
                .build());
        when(secondChannel.search(subIntent, 5)).thenReturn(SearchChannelResult.builder()
                .retrievedChunks(List.of(
                        RetrievedChunk.builder().id("chunk-2").text("OA 申请也支持移动端提交").score(0.81F).build(),
                        RetrievedChunk.builder().id("chunk-3").text("HR 系统同步审批状态").score(0.79F).build()
                ))
                .intentChunks(Map.of(
                        "intent-oa", List.of(RetrievedChunk.builder().id("chunk-2").text("OA 申请也支持移动端提交").score(0.81F).build()),
                        "intent-hr", List.of(RetrievedChunk.builder().id("chunk-3").text("HR 系统同步审批状态").score(0.79F).build())
                ))
                .build());

        SearchChannelResult result = retrievalEngine.search(subIntent, 5);

        assertThat(result.getIntentChunks()).containsKey("intent-oa");
        assertThat(result.getIntentChunks().get("intent-oa")).hasSize(2);
        assertThat(result.getIntentChunks().get("intent-oa").get(0).getText()).contains("提交审批");
        assertThat(result.getIntentChunks()).containsKey("intent-hr");
        assertThat(result.getRetrievedChunks()).hasSize(3);
    }
}
