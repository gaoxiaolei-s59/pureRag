package org.puregxl.site.rag.core.memory;

import org.puregxl.site.infra.framework.convention.ChatMessage;

import java.util.List;

public interface ConversationSummerService {



    ChatMessage loadSummary(String conversationId, String userId);

    /**
     * 生成并保存会话摘要。
     * <p>
     * 由对话记忆压缩流程调用，将旧摘要和本次待压缩历史合并为新的摘要后写入摘要表。
     *
     * @param conversationId 会话 ID
     * @param userId 用户 ID
     * @param historyMessages 待压缩的历史消息
     * @return 生成后的摘要内容，生成失败或为空时返回 null
     */
    String summarizeAndSave(String conversationId, String userId, List<ChatMessage> historyMessages);
}
