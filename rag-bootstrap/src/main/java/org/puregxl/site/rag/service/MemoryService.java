package org.puregxl.site.rag.service;

import org.puregxl.site.infra.framework.convention.ChatMessage;

import java.util.List;

public interface MemoryService {
    List<ChatMessage> loadMemory(String userId, String conversationId, ChatMessage user);

    /**
     * 保存一次完整问答结果。
     * <p>
     * 流式输出完成后调用，用于把用户问题和助手回复持久化为后续对话记忆。
     *
     * @param userId 用户 ID
     * @param conversationId 会话 ID
     * @param userMessage 用户消息
     * @param assistantMessage 助手回复
     */
    void saveConversationTurn(String userId, String conversationId, ChatMessage userMessage, ChatMessage assistantMessage);
}
