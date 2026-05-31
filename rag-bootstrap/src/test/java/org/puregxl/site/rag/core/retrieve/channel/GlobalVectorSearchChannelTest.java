package org.puregxl.site.rag.core.retrieve.channel;

import org.junit.jupiter.api.Test;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.config.SearchChannelProperties;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.NodeScoreFilters;
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

class GlobalVectorSearchChannelTest {

    @Test
    void searchUsesVectorMultiplierWhenIntentConfidenceIsLow() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        SearchChannelProperties properties = new SearchChannelProperties();
        SearchChannelProperties.Channels channels = new SearchChannelProperties.Channels();
        SearchChannelProperties.VectorChannel vectorChannel = new SearchChannelProperties.VectorChannel();
        vectorChannel.setEnabled(true);
        vectorChannel.setConfidenceThreshold(0.6D);
        vectorChannel.setTopKMultiplier(3);
        channels.setVectorChannel(vectorChannel);
        properties.setChannels(channels);
        GlobalVectorSearchChannel searchChannel = new GlobalVectorSearchChannel(
                embeddingService,
                ragRetrievalService,
                properties,
                new NodeScoreFilters(),
                knowledgeBaseMapper
        );
        SubQuestionIntent subIntent = new SubQuestionIntent("费用报销怎么申请", List.of(
                NodeScore.builder()
                        .score(0.52D)
                        .intentNode(IntentNode.builder()
                                .id("intent-finance")
                                .name("财务制度")
                                .kind(IntentKind.KB)
                                .kbId("kb-finance")
                                .collectionName("kb_collection_finance")
                                .build())
                        .build()
        ));

        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(
                KnowledgeBaseDO.builder().collectionName("kb_collection_finance").delFlag(0).build(),
                KnowledgeBaseDO.builder().collectionName("kb_collection_oa").delFlag(0).build()
        ));
        when(embeddingService.embed("费用报销怎么申请")).thenReturn(List.of(0.2F, 0.3F));
        when(ragRetrievalService.searchSimilarChunks(eq("kb_collection_finance"), any(), eq(15))).thenReturn(List.of(
                RetrievedChunk.builder().id("chunk-1").text("报销申请需要上传发票").score(0.84F).build()
        ));
        when(ragRetrievalService.searchSimilarChunks(eq("kb_collection_oa"), any(), eq(15))).thenReturn(List.of(
                RetrievedChunk.builder().id("chunk-2").text("OA 里也能发起报销流程").score(0.72F).build()
        ));

        assertThat(searchChannel.isEnabled(subIntent)).isTrue();
        SearchChannelResult result = searchChannel.search(subIntent, 5);

        assertThat(result.getIntentChunks()).isEqualTo(Map.of());
        assertThat(result.getRetrievedChunks()).hasSize(2);
        assertThat(result.getRetrievedChunks().get(0).getText()).contains("上传发票");
    }
}
