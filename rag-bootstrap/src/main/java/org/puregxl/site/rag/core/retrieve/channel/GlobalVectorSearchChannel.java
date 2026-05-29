package org.puregxl.site.rag.core.retrieve.channel;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.config.SearchChannelProperties;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.NodeScoreFilters;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.enums.SearchChannelType;
import org.puregxl.site.rag.retrieval.RagRetrievalService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全局向量检索
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GlobalVectorSearchChannel implements SearchChannel{
    private final EmbeddingService embeddingService;
    private final RagRetrievalService ragRetrievalService;
    private final SearchChannelProperties searchChannelProperties;
    private final NodeScoreFilters nodeScoreFilters;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public String getName() {
        return "global-vector-search";
    }

    @Override
    public Integer priority() {
        return 100;
    }

    @Override
    public SearchChannelResult search(SubQuestionIntent subIntent, int defaultTopK) {
        if (subIntent == null || StrUtil.isBlank(subIntent.getSubQuestion())) {
            return emptyResult();
        }

        List<Float> queryEmbedding = embeddingService.embed(subIntent.getSubQuestion().trim());
        if (CollUtil.isEmpty(queryEmbedding)) {
            return emptyResult();
        }

        List<String> collections = getAllKBCollections();
        if (CollUtil.isEmpty(collections)) {
            return emptyResult();
        }

        List<RetrievedChunk> mergedChunks = new ArrayList<>();
        for (String collectionName : collections) {
            if (StrUtil.isBlank(collectionName)) {
                continue;
            }

            int topK = resolveTopK(defaultTopK);
            List<RetrievedChunk> chunks = ragRetrievalService.searchSimilarChunks(collectionName, queryEmbedding, topK);
            if (CollUtil.isEmpty(chunks)) {
                continue;
            }
            mergedChunks.addAll(chunks);
        }

        return SearchChannelResult.builder()
                .searchChannelType(getType())
                .channelName(getName())
                .retrievedChunks(mergedChunks)
                .intentChunks(Map.of())
                .build();
    }

    @Override
    public boolean isEnabled(SubQuestionIntent subIntent) {
        return searchChannelProperties.getChannels() != null
                && searchChannelProperties.getChannels().getVectorChannel() != null
                && searchChannelProperties.getChannels().getVectorChannel().isEnabled()
                && subIntent != null
                && (CollUtil.isEmpty(nodeScoreFilters.kb(subIntent.getNodeScores()))
                || highestIntentScore(subIntent) < confidenceThreshold());
    }

    @Override
    public SearchChannelType getType() {
        return SearchChannelType.GLOBAL;
    }

    private SearchChannelResult emptyResult() {
        return SearchChannelResult.builder()
                .searchChannelType(getType())
                .channelName(getName())
                .retrievedChunks(List.of())
                .intentChunks(Map.of())
                .build();
    }

    private int resolveTopK(int defaultTopK) {
        int multiplier = searchChannelProperties.getChannels() != null
                && searchChannelProperties.getChannels().getVectorChannel() != null
                ? Math.max(searchChannelProperties.getChannels().getVectorChannel().getTopKMultiplier(), 1)
                : 1;
        return Math.max(defaultTopK, 1) * multiplier;
    }

    private double confidenceThreshold() {
        if (searchChannelProperties.getChannels() == null || searchChannelProperties.getChannels().getVectorChannel() == null) {
            return 1D;
        }
        return searchChannelProperties.getChannels().getVectorChannel().getConfidenceThreshold();
    }

    private double highestIntentScore(SubQuestionIntent subIntent) {
        if (CollUtil.isEmpty(subIntent.getNodeScores())) {
            return 0D;
        }
        return subIntent.getNodeScores().stream()
                .filter(nodeScore -> nodeScore != null)
                .mapToDouble(NodeScore::getScore)
                .max()
                .orElse(0D);
    }

    private List<String> getAllKBCollections() {
        Set<String> collections = new HashSet<>();
        List<KnowledgeBaseDO> kbList = knowledgeBaseMapper.selectList(null);
        for (KnowledgeBaseDO kb : kbList) {
            if (kb != null
                    && (kb.getDelFlag() == null || kb.getDelFlag() == 0)
                    && StrUtil.isNotBlank(kb.getCollectionName())) {
                collections.add(kb.getCollectionName().trim());
            }
        }
        return new ArrayList<>(collections);
    }
}
