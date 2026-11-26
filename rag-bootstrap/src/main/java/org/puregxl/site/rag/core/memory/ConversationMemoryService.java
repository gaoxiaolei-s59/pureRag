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
    List<ChatMessage> loadHistory(String conversationId, String userId);

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
     * 提交后台摘要任务。
     * <p>
     * 达到阈值后，滚动摘要覆盖最近原文窗口的前半段。摘要和覆盖标记一并提交；
     * 原文窗口仍完整加载，等窗口滑出覆盖范围后才会再次生成摘要。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     */
    void compressIfNeeded(String conversationId, String userId);
}
