package org.puregxl.site.rag.core.retrieve;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannel;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannelResult;
import org.puregxl.site.rag.core.retrieve.channel.SearchResultPostProcessor;
import org.springframework.stereotype.Service;

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

    private final List<SearchResultPostProcessor> searchResultPostProcessors;

    private final Executor MultiChannelRetrievalexecutor;

    /**
     * 执行单个子问题的多通道检索，并聚合所有通道结果。
     */
    public List<RetrievedChunk> search(SubQuestionIntent subIntent, int defaultTopK) {
        if (subIntent == null || StrUtil.isBlank(subIntent.getSubQuestion())) {
            return List.of();
        }
        List<SearchChannelResult> searchChannelResults = executeSearchChannel(subIntent, defaultTopK);
        return executePostProcessors(subIntent, searchChannelResults);
    }


    /**
     * 多通道结果后处理。
     * <p>
     * 初始 Chunk 顺序来自通道优先级，默认去重会保留优先级更高的结果；随后再交给 rerank 等质量增强处理器。
     */
    private List<RetrievedChunk> executePostProcessors(SubQuestionIntent subIntent,
                                                       List<SearchChannelResult> searchChannelResults) {
        List<RetrievedChunk> chunks = mergeRetrievedChunks(searchChannelResults);
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<SearchResultPostProcessor> enabledPostProcessors = safePostProcessors().stream()
                .filter(Objects::nonNull)
                .filter(SearchResultPostProcessor::isEnabled)
                .sorted(Comparator.comparingInt(SearchResultPostProcessor::getOrder))
                .toList();

        if (enabledPostProcessors.isEmpty()) {
            log.warn("没有可用的检索后处理器，直接返回原始多通道结果，chunkCount：{}", chunks.size());
            return chunks;
        }

        int startChunk = chunks.size();
        List<RetrievedChunk> processedChunks = chunks;
        for (SearchResultPostProcessor postProcessor : enabledPostProcessors) {
            try {
                processedChunks = postProcessor.process(subIntent.getSubQuestion(), processedChunks);
                if (processedChunks == null) {
                    processedChunks = List.of();
                }
                log.info("检索后处理器执行完成，name：{}，chunkCount：{}", postProcessor.getName(), processedChunks.size());
            } catch (Exception e) {
                log.error("检索后处理器执行失败，跳过当前处理器，name：{}", postProcessor.getName(), e);
            }
        }

        log.info("多通道检索后处理完成，before：{}，after：{}", startChunk, processedChunks.size());
        return processedChunks;
    }

    private List<RetrievedChunk> mergeRetrievedChunks(List<SearchChannelResult> searchChannelResults) {
        if (searchChannelResults == null || searchChannelResults.isEmpty()) {
            return List.of();
        }
        return searchChannelResults.stream()
                .filter(Objects::nonNull)
                .flatMap(result -> safeChunks(result).stream())
                .filter(Objects::nonNull)
                .toList();
    }


    /**
     * 按通道优先级筛选并并行执行检索通道。
     * <p>
     * 单个通道异常会降级为空结果，避免某一路外部检索故障拖垮整次问答。
     */
    private List<SearchChannelResult> executeSearchChannel(SubQuestionIntent subIntent, int defaultTopK) {
        List<SearchChannel> enabledSearchChannel = safeSearchChannels().stream()
                .filter(Objects::nonNull)
                .filter(searchChannel -> isChannelEnabled(searchChannel, subIntent))
                .sorted(Comparator.comparingInt(searchChannel -> safePriority(searchChannel.priority())))
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
                                SearchChannelResult result = searchChannel.search(subIntent, defaultTopK);
                                return result == null ? emptyResult(searchChannel) : result;
                            } catch (Exception e) {
                                log.error("通道执行失败，降级为空结果，name：{}", searchChannel.getName(), e);
                                return emptyResult(searchChannel);
                            }
                        }
                , MultiChannelRetrievalexecutor)).toList();

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
            int size = safeChunks(searchChannelResult).size();
            totalChunks += size;
            if (size > 0) {
                log.info("{}，成功，chunkCount：{}", searchChannelResult.getChannelName(), size);
                successCount++;
            } else {
                log.info("{}，无结果", searchChannelResult.getChannelName());
                failCount++;
            }
        }

        log.info("多通道检索统计 - 总通道数: {}, 有结果: {}, 无结果: {}, Chunk 总数: {}",
                searchChannelResults.size(), successCount, failCount, totalChunks);

        return searchChannelResults;
    }

    private boolean isChannelEnabled(SearchChannel searchChannel, SubQuestionIntent subIntent) {
        try {
            return searchChannel.isEnabled(subIntent);
        } catch (Exception e) {
            log.error("检索通道启用判断失败，跳过该通道，name：{}", searchChannel.getName(), e);
            return false;
        }
    }

    private int safePriority(Integer priority) {
        if (priority == null) {
            return Integer.MAX_VALUE;
        }
        return priority;
    }

    private List<SearchChannel> safeSearchChannels() {
        return searchChannels == null ? List.of() : searchChannels;
    }

    private List<SearchResultPostProcessor> safePostProcessors() {
        return searchResultPostProcessors == null ? List.of() : searchResultPostProcessors;
    }

    private List<RetrievedChunk> safeChunks(SearchChannelResult searchChannelResult) {
        if (searchChannelResult == null || searchChannelResult.getRetrievedChunks() == null) {
            return List.of();
        }
        return searchChannelResult.getRetrievedChunks();
    }

    private SearchChannelResult emptyResult(SearchChannel searchChannel) {
        return SearchChannelResult.builder()
                .searchChannelType(searchChannel == null ? null : searchChannel.getType())
                .channelName(searchChannel == null ? MULTI_CHANNEL_SEARCH_NAME : searchChannel.getName())
                .retrievedChunks(List.of())
                .intentChunks(Map.of())
                .build();
    }
}
