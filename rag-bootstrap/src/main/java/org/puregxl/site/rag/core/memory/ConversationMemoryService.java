package org.puregxl.site.rag.core.memory;

import org.puregxl.site.infra.framework.convention.ChatMessage;

import java.util.List;

public interface ConversationMemoryService {
    /**
     * 加载历史上下文
     * @param conversationId
     * @param userId
     * @return
     */
    List<ChatMessage>  loadHistory(String conversationId, String userId);

    /**
     * 持久化一次完整对话。
     * <p>
     * 一次对话包含一条用户消息和一条助手消息，写入数据库后用于后续 RAG 问答补充上下文。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @param userMessage 用户消息
     * @param assistantMessage 助手消息
     */
    void saveConversationTurn(String conversationId, String userId, ChatMessage userMessage, ChatMessage assistantMessage);

    /**
     * 检查当前会话是否达到压缩阈值，并执行摘要压缩。
     * <p>
     * 达到阈值后，先将超过最近保留轮数的历史消息合并进会话摘要，
     * 摘要写入成功后再把这些原始消息标记为已压缩，后续加载上下文时只加载摘要和最近未压缩消息。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     */
    void compressIfNeeded(String conversationId, String userId);
}
