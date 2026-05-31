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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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

    private final List<SearchChannel> searchChannels;

    private final Executor MultiChannelRetrievalexecutor;

    /**
     * 执行单个子问题的多通道检索，并聚合所有通道结果。
     */
    public List<RetrievedChunk> search(SubQuestionIntent subIntent, int defaultTopK) {
        if (subIntent == null || StrUtil.isBlank(subIntent.getSubQuestion())) {
            return List.of();
        }
        List<SearchChannelResult> searchChannelResults = executeSearchChannel(subIntent, defaultTopK);

        return List.of();
    }



    /**
     * 实现多通道检索 - 最终返回结果
     * @return
     */
    private List<SearchChannelResult> executeSearchChannel(SubQuestionIntent subIntent, int defaultTopK) {
        //过滤 排序
         List<SearchChannel> enabledSearchChannel = searchChannels.stream()
                 .filter(searchChannel -> searchChannel.isEnabled(subIntent))
                 .sorted(Comparator.comparingInt(SearchChannel::priority))
                 .toList();

        if (enabledSearchChannel.isEmpty()) {
            return List.of();
        }

        log.info("启用的检索通道：{}",
                enabledSearchChannel.stream().map(SearchChannel::getName).toList());

        //多通道并行返回结果
        List<CompletableFuture<SearchChannelResult>> completableFutures = enabledSearchChannel.stream()
                .map(searchChannel -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return searchChannel.search(subIntent, defaultTopK);
                            } catch (Exception e) {
                                log.error("通道执行失败, name: {}", searchChannel.getName());
                                return emptyResult();
                            }
                        }
                )).toList();

        int successCount = 0;
        int failCount = 0;
        int totalChunks = 0;

        List<SearchChannelResult> searchChannelResults = completableFutures.stream()
                .map(CompletableFuture::join)
                .toList();

        /**
         * 记录条目
         */
        for (SearchChannelResult searchChannelResult : searchChannelResults) {
            int size = searchChannelResult.getRetrievedChunks().size();
            successCount += size;
            if (size > 0) {
                log.info("{}, 成功", searchChannelResult.getChannelName());
                successCount++;
            } else {
                log.info("{}, 失败", searchChannelResult.getChannelName());
                failCount++;
            }
        }

        log.info("多通道检索统计 - 总通道数: {}, 有结果: {}, 无结果: {}, Chunk 总数: {}",
                searchChannelResults.size(), successCount, failCount, totalChunks);

        return searchChannelResults;
    }



    /**
     * 按优先级执行所有可用通道。
     * <p>
     * 这里把“选择哪些通道执行”的逻辑和“怎么合并结果”的逻辑拆开，避免主流程里同时处理两类关注点。
     */
    private List<SearchChannelResult> executeEnabledChannels(SubQuestionIntent subIntent, int defaultTopK) {
        return searchChannels.stream()
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
