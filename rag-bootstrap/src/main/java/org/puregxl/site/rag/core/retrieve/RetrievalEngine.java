package org.puregxl.site.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.NodeScoreFilters;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannelResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;


/**
 * 检索引擎
 * 负责协调多通道检索（知识库）和 MCP（模型控制协议）工具的调用，并对检索结果进行重排序和格式化，最终生成用于 LLM 的上下文
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalEngine implements RetrievalService{

    private final MultiChannelRetrievalEngine multiChannelRetrievalEngine;
    private final Executor retrievalBuildExecutor;
    private final NodeScoreFilters nodeScoreFilters;

    /**
     * 检索方法：根据子问题意图列表执行检索，整合知识库和MCP工具的结果
     */
    @Override
    public RetrievalContext retrieval(List<SubQuestionIntent> subIntents, int defaultTopK) {
        List<SubQuestionIntent> safeSubIntents = CollUtil.isEmpty(subIntents) ? List.of() : subIntents;
        List<CompletableFuture<SubQuestionContext>> tasks = safeSubIntents.stream()
                .map(subIntent -> CompletableFuture.supplyAsync(
                        () -> {
                            try{
                                return buildSubQuestionContext(subIntent, defaultTopK);
                            } catch (Exception e) {
                                log.error("子问题上下文构建失败，降级为空上下文，question：{}", subIntent.getSubQuestion(), e);
                                return new SubQuestionContext(subIntent.getSubQuestion(), "", "", Map.of());
                            }
                        },
                        retrievalBuildExecutor
                ))
                .toList();

        List<SubQuestionContext> contexts = tasks.stream()
                .map(CompletableFuture::join)
                .toList();

        String kbContext = contexts.stream()
                .map(SubQuestionContext::kbContext)
                .filter(StrUtil::isNotBlank)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");

        String mcpContext = contexts.stream()
                .map(SubQuestionContext::mcpContext)
                .filter(StrUtil::isNotBlank)
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");

        Map<String, List<RetrievedChunk>> intentChunks = new LinkedHashMap<>();
        contexts.stream()
                .map(SubQuestionContext::intentChunks)
                .filter(Objects::nonNull)
                .forEach(intentChunks::putAll);

        return RetrievalContext.builder()
                .kbContext(kbContext)
                .mcpContext(mcpContext)
                .intentChunks(intentChunks)
                .build();
    }


    /**
     * 构建SubQuestionContext
     * @param subIntent
     * @param TopK
     * @return
     */
    private SubQuestionContext buildSubQuestionContext(SubQuestionIntent subIntent, int TopK) {

        List<NodeScore> mcp = nodeScoreFilters.mcp(subIntent.getNodeScores());

        List<NodeScore> kb = nodeScoreFilters.kb(subIntent.getNodeScores());

        SearchChannelResult searchResult = multiChannelRetrievalEngine.search(subIntent, TopK);

        Map<String, List<RetrievedChunk>> intentChunks = searchResult.getIntentChunks() == null
                ? Map.of()
                : searchResult.getIntentChunks();

        String kbContext = buildKbContext(subIntent.getSubQuestion(), kb, intentChunks, searchResult.getRetrievedChunks());
        String mcpContext = "";
        return new SubQuestionContext(subIntent.getSubQuestion(), kbContext, mcpContext, intentChunks);
    }

    /**
     * 构建单个子问题的 KB 文本上下文。
     * <p>
     * 这里保留“子问题 -> 意图 -> 片段”三级结构，后续喂给大模型时更容易追溯每段知识来自哪个命中意图。
     */
    private String buildKbContext(String question,
                                  List<NodeScore> kbNodeScores,
                                  Map<String, List<RetrievedChunk>> intentChunks,
                                  List<RetrievedChunk> retrievedChunks) {
        if (StrUtil.isBlank(question)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("子问题：").append(question.trim());
        boolean appended = false;

        if (CollUtil.isNotEmpty(kbNodeScores) && CollUtil.isNotEmpty(intentChunks)) {
            for (NodeScore kbNodeScore : kbNodeScores) {
                if (kbNodeScore == null || kbNodeScore.getIntentNode() == null) {
                    continue;
                }
                String intentId = kbNodeScore.getIntentNode().getId();
                List<RetrievedChunk> chunks = intentChunks.get(intentId);
                if (CollUtil.isEmpty(chunks)) {
                    continue;
                }

                appended = true;
                builder.append("\n知识意图：").append(resolveIntentLabel(kbNodeScore));
                for (int index = 0; index < chunks.size(); index++) {
                    builder.append("\n").append(index + 1).append(". ").append(chunks.get(index).getText());
                }
            }
        }

        if (!appended && CollUtil.isNotEmpty(retrievedChunks)) {
            builder.append("\n全局向量检索结果：");
            for (int index = 0; index < retrievedChunks.size(); index++) {
                builder.append("\n").append(index + 1).append(". ").append(retrievedChunks.get(index).getText());
            }
            appended = true;
        }
        return appended ? builder.toString() : "";
    }

    private String resolveIntentLabel(NodeScore kbNodeScore) {
        if (StrUtil.isNotBlank(kbNodeScore.getIntentNode().getFullPath())) {
            return kbNodeScore.getIntentNode().getFullPath();
        }
        if (StrUtil.isNotBlank(kbNodeScore.getIntentNode().getName())) {
            return kbNodeScore.getIntentNode().getName();
        }
        return kbNodeScore.getIntentNode().getId();
    }

    /**
     * 基于过滤后的节点分数重建一个子问题意图，只保留当前通道真正可消费的节点。
     */
    private SubQuestionIntent rebuildSubIntent(SubQuestionIntent source, List<NodeScore> filteredNodeScores) {
        if (CollUtil.isEmpty(filteredNodeScores)) {
            return null;
        }
        return new SubQuestionIntent(source.getSubQuestion(), filteredNodeScores);
    }

    private record SubQuestionContext(String question,
                                      String kbContext,
                                      String mcpContext,
                                      Map<String, List<RetrievedChunk>> intentChunks) {
    }
}
