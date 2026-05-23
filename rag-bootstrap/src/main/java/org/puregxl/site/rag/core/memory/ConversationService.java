package org.puregxl.site.rag.core.memory;



public interface ConversationService {

    /**
     * 保存会话接口
     */
    void saveConversation(String conversationId, String userId, String content);
}
