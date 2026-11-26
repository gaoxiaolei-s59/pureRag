package org.puregxl.site.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.memory.ConversationMemoryService;
import org.puregxl.site.rag.core.memory.ConversationStore;
import org.puregxl.site.rag.core.memory.ConversationSummerService;
import org.puregxl.site.rag.core.usermemory.UserMemoryService;
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
import java.util.concurrent.RejectedExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryServiceImpl implements MemoryService {


    private final ConversationMemoryService conversationMemoryService;

    private final ConversationSummerService conversationSummerService;

    private final Executor memoryLoadExecutor;

    private final ConversationStore conversationStore;

    private final MemoryMapper memoryMapper;

    private final UserMemoryService userMemoryService;

    /** 读取摘要与最近历史。摘要生成在写入一轮对话之后异步执行。 */
    @Override
    public List<ChatMessage> loadMemory(String userId, String conversationId, ChatMessage user) {
        if (StrUtil.hasBlank(userId, conversationId)) {
            return List.of();
        }
        createConversationIfAbsent(conversationId, userId, user);
        try {
            CompletableFuture<ChatMessage> summary = CompletableFuture.supplyAsync(
                    () -> loadSummaryWithFallback(conversationId, userId), memoryLoadExecutor);
            List<ChatMessage> history = loadHistoryWithFallback(conversationId, userId);
            ChatMessage previousSummary = summary.join();
            if (previousSummary == null) {
                return history;
            }
            List<ChatMessage> result = new ArrayList<>(history.size() + 1);
            result.add(previousSummary);
            result.addAll(history);
            return result;
        } catch (RejectedExecutionException ex) {
            // 读取线程池繁忙时优先保留最近原文，避免因为一次调度失败而丢掉全部上下文。
            return loadHistoryWithFallback(conversationId, userId);
        }
    }

    @Override
    public ChatMessage loadUserMemory(String userId) {
        return userMemoryService.load(userId);
    }

    /**
     * 保存流式问答完成后的完整对话。
     * <p>
     * 该方法由 SSE 完成回调触发，落库失败只记录日志，不回滚或影响已完成的响应。
     */
    @Override
    public void saveConversationTurn(String userId, String conversationId, ChatMessage userMessage, ChatMessage assistantMessage) {
        if (StrUtil.hasBlank(userId, conversationId) || userMessage == null || assistantMessage == null) {
            return;
        }
        try {
            conversationMemoryService.saveConversationTurn(conversationId, userId, userMessage, assistantMessage);
        } catch (Exception e) {
            log.error("保存对话记忆失败, conversationId:{}, userId:{}", conversationId, userId, e);
            return;
        }
        try {
            userMemoryService.learn(userId);
        } catch (RuntimeException e) {
            // 本轮消息及待处理记录已经提交，后台轮询可继续处理。
            log.warn("唤醒长期记忆处理失败, userId={}", userId, e);
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
                            .orderByAsc(MemoryDO::getCreateTime, MemoryDO::getId))
                    .stream()
                    .map(this::toMemoryQueryResponse)
                    .toList();
        } catch (Exception e) {
            log.error("查询历史会话失败, conversationId:{}, userId:{}", conversionId, userId, e);
            return List.of();
        }
    }


    /** 摘要读取失败时继续使用最近对话。 */
    private ChatMessage loadSummaryWithFallback(String conversationId, String userId) {
        try {
            return conversationSummerService.loadSummary(conversationId, userId);
        } catch (Exception e) {
            log.warn("读取摘要失败, conversationId:{}, userId:{}", conversationId, userId, e);
            return null;
        }
    }


    /** 历史读取失败时保留可用的摘要。 */
    private List<ChatMessage> loadHistoryWithFallback(String conversationId, String userId) {
        try {
            List<ChatMessage> messages = conversationMemoryService.loadHistory(conversationId, userId);
            return messages == null ? List.of() : messages;
        } catch (Exception e) {
            log.warn("读取历史消息失败, conversationId:{}, userId:{}", conversationId, userId, e);
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
     * 将历史消息映射成前端最近会话详情页使用的轻量结构。
     */
    private MemoryQueryResponse toMemoryQueryResponse(MemoryDO memoryDO) {
        MemoryQueryResponse response = new MemoryQueryResponse();
        response.setRole(memoryDO.getRole());
        response.setContent(memoryDO.getContent());
        return response;
    }

}
