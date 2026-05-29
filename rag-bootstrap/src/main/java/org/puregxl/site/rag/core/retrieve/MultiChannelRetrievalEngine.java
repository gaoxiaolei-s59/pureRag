package org.puregxl.site.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.retrieval.RagRetrievalService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多通道检索引擎
 * <p>
 * 负责协调多个检索通道和后置处理器：
 * 1. 并行执行所有启用的检索通道
 * 2. 依次执行后置处理器链
 * 3. 返回最终的检索结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiChannelRetrievalEngine {

    private final EmbeddingService embeddingService;
    private final RagRetrievalService ragRetrievalService;

    /**
     * 执行单个子问题的 KB 通道检索。
     * <p>
     * 当前先实现 KB 检索：对同一个子问题只做一次向量化，然后按命中的意图节点逐个到对应 Collection 召回，
     * 最终返回「意图 ID -> Chunk 列表」的聚合结果，供上层继续拼装上下文。
     */
    public Map<String, List<RetrievedChunk>> retrieveKbByIntent(SubQuestionIntent subIntent, int defaultTopK) {
        if (subIntent == null || StrUtil.isBlank(subIntent.getSubQuestion()) || CollUtil.isEmpty(subIntent.getNodeScores())) {
            return Map.of();
        }

        List<Float> queryEmbedding = embeddingService.embed(subIntent.getSubQuestion().trim());
        if (CollUtil.isEmpty(queryEmbedding)) {
            return Map.of();
        }

        Map<String, List<RetrievedChunk>> result = new LinkedHashMap<>();
        for (NodeScore nodeScore : subIntent.getNodeScores()) {
            if (nodeScore == null || nodeScore.getIntentNode() == null) {
                continue;
            }

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
            result.put(intentId, chunks);
        }
        return result;
    }

    private int resolveTopK(NodeScore nodeScore, int defaultTopK) {
        Integer nodeTopK = nodeScore.getIntentNode().getTopK();
        if (nodeTopK != null && nodeTopK > 0) {
            return nodeTopK;
        }
        return Math.max(defaultTopK, 1);
    }
}
