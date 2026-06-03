package org.puregxl.site.rag.core.retrieve;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.NodeScoreFilters;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
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
        if (subIntent == null || StrUtil.isBlank(subIntent.getSubQuestion())) {
            return new SubQuestionContext("", "", "", Map.of());
        }
        List<NodeScore> nodeScores = CollUtil.isEmpty(subIntent.getNodeScores()) ? List.of() : subIntent.getNodeScores();

        List<NodeScore> mcp = nodeScoreFilters.mcp(nodeScores);

        List<RetrievedChunk> search = multiChannelRetrievalEngine.search(subIntent, TopK);

        String mcpContext = CollUtil.isEmpty(mcp) ? "" : executeMcpAndMerge(subIntent.getSubQuestion(), mcp);

        String kbContext = buildKbContext(subIntent.getSubQuestion(), search);

        return new SubQuestionContext(subIntent.getSubQuestion(), kbContext, mcpContext, Map.of());
    }

    /**
     * 调用MCP工具
     * @param subQuestion
     * @param mcp
     * @return
     */
    private String executeMcpAndMerge(String subQuestion, List<NodeScore> mcp) {
        // MCP 工具调用链路当前还没有接入具体 Tool Dispatcher。
        // 这里保留上下文构建边界，避免 MCP 意图影响 KB 检索主流程。
        log.warn("MCP 意图已识别但工具调用链路尚未接入，subQuestion：{}，mcpCount：{}",
                subQuestion, CollUtil.isEmpty(mcp) ? 0 : mcp.size());
        return "";
    }

    /**
     * 构建单个子问题的 KB 文本上下文。
     * <p>
     * 多通道检索引擎返回的 Chunk 已经经过通道合并、去重和 rerank，
     * 到这里就是最终准备喂给大模型的知识片段，因此只保留稳定、简洁的 Prompt 格式。
     */
    private String buildKbContext(String question, List<RetrievedChunk> retrievedChunks) {
        if (StrUtil.isBlank(question) || CollUtil.isEmpty(retrievedChunks)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("子问题：").append(question.trim());
        builder.append("\n知识库检索结果：");
        int displayIndex = 1;
        for (RetrievedChunk retrievedChunk : retrievedChunks) {
            if (retrievedChunk == null || StrUtil.isBlank(retrievedChunk.getText())) {
                continue;
            }
            builder.append("\n").append(displayIndex++).append(". ").append(retrievedChunk.getText().trim());
        }
        return displayIndex > 1 ? builder.toString() : "";
    }

    private record SubQuestionContext(String question,
                                      String kbContext,
                                      String mcpContext,
                                      Map<String, List<RetrievedChunk>> intentChunks) {
    }
}
