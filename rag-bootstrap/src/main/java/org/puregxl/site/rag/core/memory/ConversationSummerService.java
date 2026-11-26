package org.puregxl.site.rag.core.memory;

import org.puregxl.site.infra.framework.convention.ChatMessage;

public interface ConversationSummerService {
    ChatMessage loadSummary(String conversationId, String userId);

    /** 在写入完整问答并提交事务后异步刷新滚动摘要。 */
    void scheduleCompression(String conversationId, String userId);
}
