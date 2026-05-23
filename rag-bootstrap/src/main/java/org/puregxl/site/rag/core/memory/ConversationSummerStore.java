package org.puregxl.site.rag.core.memory;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.util.LLMResponseCleaner;
import org.puregxl.site.rag.dao.entity.ConversationSummerDO;
import org.puregxl.site.rag.dao.mapper.ConversationSummerMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationSummerStore implements ConversationSummerService {

    private final ConversationSummerMapper conversationSummerMapper;

    private final LLMService llmService;

    private final MemoryProperties memoryProperties;

    /**
     * 加载用户历史上下文
     * @param conversationId
     * @param userId
     * @return
     */
    @Override
    public ChatMessage loadSummary(String conversationId, String userId) {
        if (StrUtil.hasBlank(conversationId, userId)) {
            return null;
        }
        LambdaQueryWrapper<ConversationSummerDO> eq = Wrappers.lambdaQuery(ConversationSummerDO.class)
                .eq(ConversationSummerDO::getUserId, userId)
                .eq(ConversationSummerDO::getConversationId, conversationId)
                .last("limit 1");

        ConversationSummerDO conversationSummerDO = conversationSummerMapper.selectOne(eq);
        if (conversationSummerDO == null || StrUtil.isBlank(conversationSummerDO.getSummaryContent())) {
            return null;
        }
        return new ChatMessage(ChatMessage.Role.SYSTEM, conversationSummerDO.getSummaryContent());
    }

    /**
     * 生成并保存会话摘要。
     * <p>
     * 压缩时会读取已有摘要，将旧摘要和本次待压缩消息一起交给大模型生成新的滚动摘要；
     * 摘要写入成功后，调用方再标记原始消息为已压缩，避免摘要失败时丢失历史上下文。
     */
    @Override
    public String summarizeAndSave(String conversationId, String userId, List<ChatMessage> historyMessages) {
        if (StrUtil.hasBlank(conversationId, userId) || historyMessages == null || historyMessages.isEmpty()) {
            return null;
        }
        ConversationSummerDO existingSummary = selectSummary(conversationId, userId);
        String summary = generateSummary(existingSummary == null ? null : existingSummary.getSummaryContent(), historyMessages);
        if (StrUtil.isBlank(summary)) {
            return null;
        }
        saveSummary(conversationId, userId, existingSummary, summary);
        return summary;
    }

    private ConversationSummerDO selectSummary(String conversationId, String userId) {
        LambdaQueryWrapper<ConversationSummerDO> queryWrapper = Wrappers.lambdaQuery(ConversationSummerDO.class)
                .eq(ConversationSummerDO::getUserId, userId)
                .eq(ConversationSummerDO::getConversationId, conversationId)
                .last("limit 1");
        return conversationSummerMapper.selectOne(queryWrapper);
    }

    private String generateSummary(String existingSummary, List<ChatMessage> historyMessages) {
        String historyText = buildHistoryText(historyMessages);
        String prompt = """
                请把下面的历史对话压缩成一段可继续对话使用的长期记忆摘要。
                要求：
                1. 保留用户偏好、事实约束、已经确认的结论、未完成事项。
                2. 删除寒暄、重复表达和无关细节。
                3. 摘要用中文，控制在 %d 字以内。
                4. 只输出摘要正文，不要输出标题、列表编号或 Markdown 代码块。

                已有摘要：
                %s

                本次新增历史：
                %s
                """.formatted(safeSummaryMaxChars(), StrUtil.blankToDefault(existingSummary, "无"), historyText);
        String rawSummary = llmService.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.user(prompt)))
                .temperature(0.1D)
                .topP(0.8D)
                .build());
        return StrUtil.sub(LLMResponseCleaner.stripMarkdownCodeFence(rawSummary), 0, safeSummaryMaxChars());
    }

    private String buildHistoryText(List<ChatMessage> historyMessages) {
        StringBuilder historyBuilder = new StringBuilder();
        for (ChatMessage message : historyMessages) {
            if (message == null || message.getRole() == null || StrUtil.isBlank(message.getContent())) {
                continue;
            }
            historyBuilder.append(message.getRole().name().toLowerCase())
                    .append(": ")
                    .append(message.getContent())
                    .append("\n");
        }
        return historyBuilder.toString();
    }

    private void saveSummary(String conversationId, String userId, ConversationSummerDO existingSummary, String summary) {
        if (existingSummary == null) {
            conversationSummerMapper.insert(ConversationSummerDO.builder()
                    .conversationId(conversationId)
                    .UserId(userId)
                    .SummaryContent(summary)
                    .build());
            return;
        }
        LambdaUpdateWrapper<ConversationSummerDO> updateWrapper = Wrappers.lambdaUpdate(ConversationSummerDO.class)
                .set(ConversationSummerDO::getSummaryContent, summary)
                .eq(ConversationSummerDO::getId, existingSummary.getId());
        conversationSummerMapper.update(updateWrapper);
    }

    private int safeSummaryMaxChars() {
        Integer summaryMaxChars = memoryProperties.getSummaryMaxChars();
        return summaryMaxChars == null || summaryMaxChars <= 0 ? 200 : summaryMaxChars;
    }
}
