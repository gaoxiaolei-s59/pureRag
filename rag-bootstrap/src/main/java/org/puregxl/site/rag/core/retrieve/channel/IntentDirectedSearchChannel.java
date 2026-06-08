package org.puregxl.site.rag.core.retrieve.channel;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意向图定向检索
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntentDirectedSearchChannel implements SearchChannel{

    private final EmbeddingService embeddingService;
    private final RagRetrievalService ragRetrievalService;
    private final SearchChannelProperties searchChannelProperties;
    private final NodeScoreFilters nodeScoreFilters;

    @Override
    public String getName() {
        return "intent-directed-search";
    }

    @Override
    public Integer priority() {
        return 10;
    }

    @Override
    public SearchChannelResult search(SubQuestionIntent subIntent, int defaultTopK) {
        if (subIntent == null || StrUtil.isBlank(subIntent.getSubQuestion())) {
            return emptyResult();
        }

        List<NodeScore> kbNodeScores = nodeScoreFilters.kb(subIntent.getNodeScores()).stream()
                .filter(nodeScore -> nodeScore.getScore() >= minIntentScore())
                .toList();
        if (CollUtil.isEmpty(kbNodeScores)) {
            return emptyResult();
        }

        List<Float> queryEmbedding = embeddingService.embed(subIntent.getSubQuestion().trim());
        if (CollUtil.isEmpty(queryEmbedding)) {
            return emptyResult();
        }

        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
        List<RetrievedChunk> mergedChunks = new ArrayList<>();
        for (NodeScore nodeScore : kbNodeScores) {
            String intentId = nodeScore.getIntentNode().getId();
            String collectionName = nodeScore.getIntentNode().getCollectionName();
            if (StrUtil.hasBlank(intentId, collectionName)) {
                continue;
            }

            int topK = resolveTopK(nodeScore, defaultTopK);
            List<RetrievedChunk> chunks = ragRetrievalService.searchSimilarChunks(collectionName, queryEmbedding, topK);
            if (CollUtil.isEmpty(chunks)) {
                continue;
            }
            intentChunks.put(intentId, chunks);
            mergedChunks.addAll(chunks);
        }

        return SearchChannelResult.builder()
                .searchChannelType(getType())
                .channelName(getName())
                .retrievedChunks(mergedChunks)
                .intentChunks(intentChunks)
                .build();
    }

    @Override
    public boolean isEnabled(SubQuestionIntent subIntent) {
        // 检查配置是否启用
        if (!searchChannelProperties.getChannels().getIntentChannel().isEnabled()) {
            return false;
        }

        if (!extractKbIntents(subIntent)) {
            return false;
        }

        return true;
    }

    public boolean extractKbIntents(SubQuestionIntent subIntent) {
        List<NodeScore> nodeScores = subIntent.getNodeScores();
        for (NodeScore nodeScore : nodeScores) {
            if (nodeScore.getIntentNode().isKB()) {
                return true;
            }
        }
        return false;
    }



    @Override
    public SearchChannelType getType() {
        return SearchChannelType.VECTOR;
    }

    private SearchChannelResult emptyResult() {
        return SearchChannelResult.builder()
                .searchChannelType(getType())
                .channelName(getName())
                .retrievedChunks(List.of())
                .intentChunks(Map.of())
                .build();
    }

    private int resolveTopK(NodeScore nodeScore, int defaultTopK) {
        Integer nodeTopK = nodeScore.getIntentNode().getTopK();
        if (nodeTopK != null && nodeTopK > 0) {
            return nodeTopK;
        }
        int multiplier = searchChannelProperties.getChannels() != null
                && searchChannelProperties.getChannels().getIntentChannel() != null
                ? Math.max(searchChannelProperties.getChannels().getIntentChannel().getTopKMultiplier(), 1)
                : 1;
        return Math.max(defaultTopK, 1) * multiplier;
    }

    private double minIntentScore() {
        if (searchChannelProperties.getChannels() == null || searchChannelProperties.getChannels().getIntentChannel() == null) {
            return 0D;
        }
        return searchChannelProperties.getChannels().getIntentChannel().getMinIntentScore();
    }
}
