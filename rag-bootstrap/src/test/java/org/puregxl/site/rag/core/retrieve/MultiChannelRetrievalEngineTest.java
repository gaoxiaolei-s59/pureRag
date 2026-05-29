package org.puregxl.site.rag.core.retrieve;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.retrieval.RagRetrievalService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiChannelRetrievalEngineTest {

    @Test
    void retrieveKbByIntentUsesIntentCollectionAndNodeTopK() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        MultiChannelRetrievalEngine retrievalEngine = new MultiChannelRetrievalEngine(embeddingService, ragRetrievalService);
        SubQuestionIntent subIntent = new SubQuestionIntent("OA 怎么申请", List.of(
                NodeScore.builder()
                        .score(0.92D)
                        .intentNode(IntentNode.builder()
                                .id("intent-oa")
                                .name("OA 流程")
                                .kind(IntentKind.KB)
                                .kbId("kb-oa")
                                .collectionName("kb_collection_oa")
                                .topK(3)
                                .build())
                        .build()
        ));

        when(embeddingService.embed("OA 怎么申请")).thenReturn(List.of(0.1F, 0.2F));
        when(ragRetrievalService.searchSimilarChunks(eq("kb_collection_oa"), any(), eq(3))).thenReturn(List.of(
                RetrievedChunk.builder().id("chunk-1").text("OA 申请需要先提交审批").score(0.93F).build()
        ));

        Map<String, List<RetrievedChunk>> result = retrievalEngine.retrieveKbByIntent(subIntent, 5);

        assertThat(result).containsKey("intent-oa");
        assertThat(result.get("intent-oa")).hasSize(1);
        assertThat(result.get("intent-oa").get(0).getText()).contains("提交审批");
    }
}
