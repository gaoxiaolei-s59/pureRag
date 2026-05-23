package org.puregxl.site.rag.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.memory.ConversationMemoryService;
import org.puregxl.site.rag.core.memory.ConversationStore;
import org.puregxl.site.rag.core.memory.ConversationSummerService;
import org.puregxl.site.rag.service.MemoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryServiceImpl implements MemoryService {


    private final ConversationMemoryService conversationMemoryService;

    private final ConversationSummerService conversationSummerService;

    private final Executor memoryLoadExecutor;

    private final ConversationStore conversationStore;

    /**
     * 加载一次对话所需的记忆上下文。
     * <p>
     * 主流程：先检查当前会话是否需要压缩历史消息，再并行加载摘要和未压缩历史，
     * 最后按「摘要 -> 历史」顺序返回给 RAG Pipeline 组装模型请求。
     * 当前用户消息不在这里追加，避免调用方重复放入本轮问题。
     *
     * @param userId
     * @param conversationId
     * @param user
     * @return
     */
    @Override
    public List<ChatMessage> loadMemory(String userId, String conversationId, ChatMessage user) {
        compressWithFallback(conversationId, userId);
        createConversationIfAbsent(conversationId, userId, user);
        Long startTime = System.currentTimeMillis();
        try {


            CompletableFuture<ChatMessage> summaryFuture = CompletableFuture.supplyAsync(
                    () -> loadSummaryWithFallback(conversationId, userId), memoryLoadExecutor
            );

            CompletableFuture<List<ChatMessage>> historyFuture = CompletableFuture.supplyAsync(
                    () -> loadHistoryWithFallback(conversationId, userId), memoryLoadExecutor
            );

            return CompletableFuture.allOf(summaryFuture, historyFuture)
                    .thenApply(v -> {
                        ChatMessage summer = summaryFuture.join();
                        List<ChatMessage> history = historyFuture.join();
                        log.info("加载会话记忆 - conversationId:{} - userId:{}, - 摘要:{} -  历史消息数量:{} -  消耗时间:{}",
                                conversationId, userId, summer != null, history, System.currentTimeMillis() - startTime);
                        List<ChatMessage> result = new ArrayList<>();
                        if (summer != null) {
                            result.add(summer);
                        }
                        result.addAll(history);
                        return result;
                    }).join();
        } catch (Exception e) {
            log.error("加载对话记忆失败 - conversationId: {}, userId: {}", conversationId, userId, e);
            return List.of();
        }
    }

    /**
     * 保存流式问答完成后的完整对话。
     * <p>
     * 该方法由 SSE 完成回调触发，落库失败只记录日志，不回滚或影响已完成的响应。
     */
    @Override
    public void saveConversationTurn(String userId, String conversationId, ChatMessage userMessage, ChatMessage assistantMessage) {
        try {
            conversationMemoryService.saveConversationTurn(conversationId, userId, userMessage, assistantMessage);
        } catch (Exception e) {
            log.error("保存对话记忆失败, conversationId:{}, userId:{}", conversationId, userId, e);
        }
    }


    /**
     * 加载摘要上下文
     * @return
     */
    private ChatMessage loadSummaryWithFallback(String conversationId, String userId) {

         try{
             ChatMessage messages = conversationSummerService.loadSummary(conversationId, userId);
             return messages;
         } catch (Exception e) {
             log.error("获取摘要总结失败, conversationId:{}, userId:{}", conversationId, userId);
             return null;
         }
    }


    /**
     * 加载历史对话
     * @return
     */
    private List<ChatMessage> loadHistoryWithFallback(String conversationId, String userId) {
        try{
            List<ChatMessage> messages = conversationMemoryService.loadHistory(conversationId, userId);
            return messages == null ? List.of() : messages;
        } catch (Exception e) {
            log.error("获取历史记录对话失败, conversationId:{}, userId:{}", conversationId, userId);
            return List.of();
        }
    }


    /**
     * 如果是第一次发起对话，持久化会话
     * @param conversationId
     * @param userId
     */
    private void createConversationIfAbsent(String conversationId, String userId, ChatMessage user) {
        try {
            conversationStore.saveConversation(conversationId, userId, user.getContent());
        } catch (Exception e) {
            log.error("持久化对话失败, conversationId:{}, userId:{}", conversationId, userId, e);
        }
    }

    /**
     * 检查是否需要压缩上下文
     * @param conversationId
     * @param userId
     */
    private void compressWithFallback(String conversationId, String userId) {
        try {
            conversationMemoryService.compressIfNeeded(conversationId, userId);
        } catch (Exception e) {
            log.error("压缩历史记录标记失败, conversationId:{}, userId:{}", conversationId, userId, e);
        }
    }


}
