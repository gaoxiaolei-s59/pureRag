package org.puregxl.site.rag.core.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.rag.dao.entity.MemoryDO;
import org.puregxl.site.rag.dao.mapper.MemoryMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConversationMemoryStore implements ConversationMemoryService {

    private final MemoryMapper memoryMapper;

    private final MemoryProperties memoryProperties;

    private final ConversationSummerService conversationSummerService;

    private static final int COMPRESSED = 1;

    /**
     * 加载未压缩的最近历史记录。
     * <p>
     * 查询时只取 compressed_flag=0 的消息，并按配置保留最近 N 轮（user + assistant 视为一轮）。
     * 数据库先按创建时间倒序截取最近消息，再在内存中反转成正序，保证传给模型的上下文顺序自然。
     *
     * @param conversationId
     * @param userId
     * @return
     */
    @Override
    public List<ChatMessage> loadHistory(String conversationId, String userId) {
        if (StrUtil.hasBlank(conversationId, userId)) {
            return List.of();
        }
        int keepMessages = safeTurnCount(memoryProperties.getHistoryKeepTurns()) * 2;
        LambdaQueryWrapper<MemoryDO> memoryDOLambdaQueryWrapper = Wrappers.lambdaQuery(MemoryDO.class)
                .eq(MemoryDO::getConversationId, conversationId)
                .eq(MemoryDO::getUserId, userId)
                .eq(MemoryDO::getCompressedFlag, 0)
                .orderByDesc(MemoryDO::getCreateTime)
                .last("limit " + keepMessages);

        List<MemoryDO> memoryDOS = memoryMapper.selectList(memoryDOLambdaQueryWrapper);
        if (CollUtil.isEmpty(memoryDOS)) {
            return List.of();
        }

        List<MemoryDO> orderedMemoryDOS = new ArrayList<>(memoryDOS);
        Collections.reverse(orderedMemoryDOS);
        return orderedMemoryDOS.stream()
                .map(this::toChatMessage)
                .toList();
    }

    /**
     * 持久化一次完整对话。
     * <p>
     * 这里不与 SSE 响应事务绑定：流式输出已经完成后再落库，失败只影响后续上下文，不影响本次用户响应。
     * 写入后立即检查压缩阈值，避免长会话持续携带过多原文历史。
     */
    @Override
    public void saveConversationTurn(String conversationId, String userId, ChatMessage userMessage, ChatMessage assistantMessage) {
        if (StrUtil.hasBlank(conversationId, userId) || userMessage == null || assistantMessage == null) {
            return;
        }
        memoryMapper.insert(toMemoryDO(conversationId, userId, userMessage));
        memoryMapper.insert(toMemoryDO(conversationId, userId, assistantMessage));
        compressIfNeeded(conversationId, userId);
    }

    /**
     * 将超过最近保留范围的未压缩消息压缩为摘要，并标记为已压缩。
     * <p>
     * 压缩顺序必须是：先生成并保存摘要，再标记原始消息 compressed_flag=1。
     * 这样即使摘要生成失败，原始历史仍然保持未压缩状态，后续对话不会丢失上下文。
     */
    @Override
    public void compressIfNeeded(String conversationId, String userId) {
        if (!Boolean.TRUE.equals(memoryProperties.getSummaryEnabled()) || StrUtil.hasBlank(conversationId, userId)) {
            return;
        }
        int keepMessages = safeTurnCount(memoryProperties.getHistoryKeepTurns()) * 2;
        LambdaQueryWrapper<MemoryDO> memoryDOLambdaQueryWrapper = Wrappers.lambdaQuery(MemoryDO.class)
                .eq(MemoryDO::getConversationId, conversationId)
                .eq(MemoryDO::getUserId, userId)
                .eq(MemoryDO::getCompressedFlag, 0)
                .orderByAsc(MemoryDO::getCreateTime);

        List<MemoryDO> memoryDOS = memoryMapper.selectList(memoryDOLambdaQueryWrapper);
        if ( CollUtil.size(memoryDOS) <= keepMessages) {
            return;
        }

        List<MemoryDO> compressMemoryList = memoryDOS.stream()
                .limit(memoryDOS.size() - keepMessages)
                .toList();

        String summary = conversationSummerService.summarizeAndSave(
                conversationId,
                userId,
                compressMemoryList.stream().map(this::toChatMessage).toList());
        if (StrUtil.isBlank(summary)) {
            log.warn("[对话记忆] 历史消息摘要生成为空，跳过压缩标记, conversationId={}, userId={}", conversationId, userId);
            return;
        }
        List<String> compressedIds = compressMemoryList.stream()
                .map(MemoryDO::getId)
                .toList();
        if (CollUtil.isEmpty(compressedIds)) {
            return;
        }
        LambdaUpdateWrapper<MemoryDO> updateWrapper = Wrappers.lambdaUpdate(MemoryDO.class)
                .set(MemoryDO::getCompressedFlag, COMPRESSED)
                .in(MemoryDO::getId, compressedIds);
        memoryMapper.update(updateWrapper);
        log.info("[对话记忆] 历史消息已标记压缩, conversationId={}, userId={}, count={}", conversationId, userId, compressedIds.size());
    }



    private MemoryDO toMemoryDO(String conversationId, String userId, ChatMessage message) {
        return MemoryDO.builder()
                .conversationId(conversationId)
                .UserId(userId)
                .role(message.getRole().name().toLowerCase())
                .content(message.getContent())
                .thinkingContent(message.getThinkingContent())
                .thinkingDuration(message.getThinkingDuration())
                .build();
    }

    private ChatMessage toChatMessage(MemoryDO memoryDO) {
        ChatMessage chatMessage = new ChatMessage(ChatMessage.Role.fromString(memoryDO.getRole()), memoryDO.getContent());
        chatMessage.setThinkingContent(memoryDO.getThinkingContent());
        chatMessage.setThinkingDuration(memoryDO.getThinkingDuration());
        return chatMessage;
    }

    private int safeTurnCount(Integer value) {
        return value == null || value <= 0 ? 1 : value;
    }
}
