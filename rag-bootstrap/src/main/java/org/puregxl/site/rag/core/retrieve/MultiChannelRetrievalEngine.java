package org.puregxl.site.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannel;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannelResult;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.Objects;

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

    private static final String MULTI_CHANNEL_SEARCH_NAME = "multi-channel-search";

    private final List<SearchChannel> searchChannel;

    /**
     * 执行单个子问题的多通道检索，并聚合所有通道结果。
     */
    public SearchChannelResult search(SubQuestionIntent subIntent, int defaultTopK) {
        if (subIntent == null || StrUtil.isBlank(subIntent.getSubQuestion())) {
            return emptyResult();
        }

        // 1.判断分数最高的节点如果低confidenceThreshold 进入全局检索
        // 2.

        List<RetrievedChunk> retrievedChunks = new ArrayList<>();
        Map<String, List<RetrievedChunk>> chunksByIntent = new LinkedHashMap<>();
        List<SearchChannelResult> channelResults = executeEnabledChannels(subIntent, defaultTopK);

        channelResults.forEach(channelResult -> mergeChannelResult(channelResult, retrievedChunks, chunksByIntent));

        return SearchChannelResult.builder()
                .channelName(MULTI_CHANNEL_SEARCH_NAME)
                .retrievedChunks(retrievedChunks)
                .intentChunks(chunksByIntent)
                .build();
    }

    /**
     * 兼容当前仅需要按意图分组结果的调用方。
     */
    public Map<String, List<RetrievedChunk>> retrieveKbByIntent(SubQuestionIntent subIntent, int defaultTopK) {
        return search(subIntent, defaultTopK).getIntentChunks();
    }

    /**
     * 按优先级执行所有可用通道。
     * <p>
     * 这里把“选择哪些通道执行”的逻辑和“怎么合并结果”的逻辑拆开，避免主流程里同时处理两类关注点。
     */
    private List<SearchChannelResult> executeEnabledChannels(SubQuestionIntent subIntent, int defaultTopK) {
        return searchChannel.stream()
                .sorted(Comparator.comparing(SearchChannel::priority))
                .filter(Objects::nonNull)
                .filter(channel -> channel.isEnabled(subIntent))
                .map(channel -> channel.search(subIntent, defaultTopK))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 把单个通道的输出合并到总结果中。
     * <p>
     * 这里同时维护两份视图：
     * 1. 扁平 Chunk 列表：方便全局检索兜底场景直接拼上下文；
     * 2. 按意图分组结果：方便定向检索按意图构建结构化上下文。
     */
    private void mergeChannelResult(SearchChannelResult channelResult,
                                    List<RetrievedChunk> retrievedChunks,
                                    Map<String, List<RetrievedChunk>> chunksByIntent) {
        if (CollUtil.isNotEmpty(channelResult.getRetrievedChunks())) {
            retrievedChunks.addAll(channelResult.getRetrievedChunks());
        }
        if (channelResult.getIntentChunks() == null) {
            return;
        }
        channelResult.getIntentChunks().forEach((intentId, chunks) ->
                chunksByIntent.merge(intentId, chunks, this::mergeChunkLists));
    }

    private List<RetrievedChunk> mergeChunkLists(List<RetrievedChunk> existing, List<RetrievedChunk> incoming) {
        List<RetrievedChunk> merged = new ArrayList<>(existing);
        merged.addAll(incoming);
        return merged;
    }

    private SearchChannelResult emptyResult() {
        return SearchChannelResult.builder()
                .channelName(MULTI_CHANNEL_SEARCH_NAME)
                .retrievedChunks(List.of())
                .intentChunks(Map.of())
                .build();
    }
}
