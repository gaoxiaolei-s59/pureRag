package org.puregxl.site.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.memory.ConversationMemoryService;
import org.puregxl.site.rag.core.memory.ConversationStore;
import org.puregxl.site.rag.core.memory.ConversationSummerService;
import org.puregxl.site.rag.dao.entity.MemoryDO;
import org.puregxl.site.rag.dao.mapper.MemoryMapper;
import org.puregxl.site.rag.dto.resp.MemoryQueryResponse;
import org.puregxl.site.rag.service.MemoryService;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;
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

    private final MemoryMapper memoryMapper;

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
     * 查询对应会话的聊天记录
     * <p>
     * 这里按“当前登录用户 + 会话 ID”查询完整历史，避免前端点击最近会话时只拿到未压缩窗口消息，
     * 也避免跨用户读取到不属于当前账号的会话内容。
     *
     * @param conversionId
     * @return
     */
    @Override
    public List<MemoryQueryResponse> queryAllChatMessage(String conversionId) {
        UserInfoDTO userContext = UserContext.getUserContext();
        String userId = userContext == null ? null : userContext.getUserId();
        if (StrUtil.hasBlank(conversionId, userId)) {
            return List.of();
        }
        try {
            return memoryMapper.selectList(Wrappers.lambdaQuery(MemoryDO.class)
                            .eq(MemoryDO::getConversationId, conversionId)
                            .eq(MemoryDO::getUserId, userId)
                            .orderByAsc(MemoryDO::getCreateTime))
                    .stream()
                    .map(this::toMemoryQueryResponse)
                    .toList();
        } catch (Exception e) {
            log.error("查询历史会话失败, conversationId:{}, userId:{}", conversionId, userId, e);
            return List.of();
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

    /**
     * 将历史消息映射成前端最近会话详情页使用的轻量结构。
     */
    private MemoryQueryResponse toMemoryQueryResponse(MemoryDO memoryDO) {
        MemoryQueryResponse response = new MemoryQueryResponse();
        response.setRole(memoryDO.getRole());
        response.setContent(memoryDO.getContent());
        return response;
    }

}
