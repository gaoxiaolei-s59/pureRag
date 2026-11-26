package org.puregxl.site.rag.core.memory;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.core.usermemory.UserMemoryQueue;
import org.puregxl.site.rag.dao.entity.MemoryDO;
import org.puregxl.site.rag.dao.mapper.ConversationMapper;
import org.puregxl.site.rag.dao.mapper.MemoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ConversationMemoryStore implements ConversationMemoryService {
    private final MemoryMapper memoryMapper;
    private final ConversationMapper conversationMapper;
    private final MemoryProperties memoryProperties;
    private final ConversationSummerService conversationSummerService;
    private final UserMemoryQueue userMemoryQueue;

    /**
     * 回放最近的原文窗口。摘要可以覆盖窗口的一部分；已经被摘要覆盖的消息仍能提供原文细节。
     * 同一时间戳用主键稳定排序，并跳过旧数据中的空消息、无效角色和窗口开头的孤立回复。
     */
    @Override
    public List<ChatMessage> loadHistory(String conversationId, String userId) {
        if (StrUtil.hasBlank(conversationId, userId)) {
            return List.of();
        }
        List<MemoryDO> rows = memoryMapper.selectList(Wrappers.lambdaQuery(MemoryDO.class)
                .eq(MemoryDO::getConversationId, conversationId)
                .eq(MemoryDO::getUserId, userId)
                .in(MemoryDO::getRole, "user", "assistant")
                .orderByDesc(MemoryDO::getCreateTime, MemoryDO::getId)
                .last("limit " + memoryProperties.getHistoryKeepTurns() * 2));

        List<ChatMessage> messages = new ArrayList<>(rows.size());
        for (int i = rows.size() - 1; i >= 0; i--) {
            MemoryDO row = rows.get(i);
            if (StrUtil.isBlank(row.getContent())) {
                continue;
            }
            if ("user".equalsIgnoreCase(row.getRole())) {
                messages.add(ChatMessage.user(row.getContent()));
            } else if (!messages.isEmpty() && "assistant".equalsIgnoreCase(row.getRole())) {
                messages.add(ChatMessage.assistant(row.getContent()));
            }
        }
        return messages;
    }

    /**
     * 将问答两条原文与长期记忆待办作为同一事务提交。回滚时后台看不到半轮记录；
     * 会话摘要在提交之后进入独立线程池，模型调用不会占用数据库连接或阻塞 SSE 完成回调。
     */
    @Override
    @Transactional
    public void saveConversationTurn(String conversationId, String userId, ChatMessage userMessage, ChatMessage assistantMessage) {
        if (StrUtil.hasBlank(conversationId, userId) || userMessage == null || assistantMessage == null
                || StrUtil.hasBlank(userMessage.getContent(), assistantMessage.getContent())) {
            return;
        }
        // 行锁持有至整轮提交，同一会话的两个请求不会交错插入四条消息。
        var fallback = ConversationStore.fallbackConversationTitle(userMessage.getContent(), memoryProperties.getTitleMaxLength());
        conversationMapper.touchForTurn(conversationId, userId, fallback.title(), fallback.description());
        conversationMapper.lockOwnedConversation(conversationId, userId);
        MemoryDO question = toMemoryDO(conversationId, userId, userMessage);
        memoryMapper.insert(question);
        memoryMapper.insert(toMemoryDO(conversationId, userId, assistantMessage));
        userMemoryQueue.enqueue(userId, question.getId());
        if (Boolean.TRUE.equals(memoryProperties.getSummaryEnabled())) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    compressIfNeeded(conversationId, userId);
                }
            });
        }
    }

    @Override
    public void compressIfNeeded(String conversationId, String userId) {
        conversationSummerService.scheduleCompression(conversationId, userId);
    }

    private MemoryDO toMemoryDO(String conversationId, String userId, ChatMessage message) {
        return MemoryDO.builder()
                .conversationId(conversationId)
                .UserId(userId)
                .role(message.getRole().name().toLowerCase(Locale.ROOT))
                .content(message.getContent())
                .thinkingContent(message.getThinkingContent())
                .thinkingDuration(message.getThinkingDuration())
                .build();
    }
}
