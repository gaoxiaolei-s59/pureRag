package org.puregxl.site.rag.core.memory;



public interface ConversationStoreService {

    /**
     * 保存会话接口
     */
    void saveConversation(String conversationId, String userId, String content);
}
