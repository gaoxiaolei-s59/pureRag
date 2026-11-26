package org.puregxl.site.rag.core.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.util.LLMResponseCleaner;
import org.puregxl.site.rag.dao.entity.ConversationDO;
import org.puregxl.site.rag.dao.mapper.ConversationMapper;
import org.puregxl.site.rag.support.PromptTemplateLoader;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 会话持久层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationStore implements ConversationStoreService {

    private static final int DESCRIPTION_MAX_LENGTH = 120;
    private static final String TITLE_PROMPT_RESOURCE_PATH = "prompt/conversation-title-prompt.txt";

    private final ConversationMapper conversationMapper;

    private final LLMService llmService;

    private final PromptTemplateLoader promptTemplateLoader;

    private final MemoryProperties memoryProperties;

    /**
     * 首次对话时保存会话元信息。
     * <p>
     * 会话表只保存会话标题、描述、归属用户等元数据，不保存消息正文；消息正文由 t_memory 维护。
     * 这里先按 conversationId + userId 查询，避免同一个会话在加载记忆、多次重试时重复插入。
     * 如果会话不存在，则基于首条用户问题调用大模型生成简短标题和描述；模型调用失败时使用首问截断兜底，
     * 保证会话列表至少能展示一个可识别的名称。
     */
    @Override
    public void saveConversation(String conversationId, String userId, String content) {
        if (StrUtil.hasBlank(conversationId, userId, content)) {
            return;
        }

        LambdaQueryWrapper<ConversationDO> conversationDOLambdaQueryWrapper = Wrappers.lambdaQuery(ConversationDO.class)
                .eq(ConversationDO::getId, conversationId)
                .eq(ConversationDO::getUserId, userId)
                .last("limit 1");

        ConversationDO conversationDO = conversationMapper.selectOne(conversationDOLambdaQueryWrapper);
        if (conversationDO != null) {
            return;
        }

        ConversationTitle conversationTitle = generateConversationTitle(content);
        ConversationDO build = ConversationDO.builder()
                .id(conversationId)
                .userId(userId)
                .title(conversationTitle.title())
                .description(conversationTitle.description())
                .deepThinking(0)
                .pinned(0)
                .build();

        conversationMapper.insert(build);
    }

    private ConversationTitle generateConversationTitle(String content) {
        try {
            String rawResult = llmService.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(buildTitlePrompt(content))))
                    .temperature(0.1D)
                    .topP(0.8D)
                    .maxTokens(120)
                    .build());
            return parseConversationTitle(rawResult, content);
        } catch (Exception ex) {
            log.warn("[会话] 生成会话标题失败，使用首问兜底", ex);
            return fallbackConversationTitle(content, memoryProperties.getTitleMaxLength());
        }
    }

    private String buildTitlePrompt(String content) {
        return promptTemplateLoader.load(TITLE_PROMPT_RESOURCE_PATH)
                .replace("{{titleMaxLength}}", String.valueOf(memoryProperties.getTitleMaxLength()))
                .replace("{{question}}", content);
    }

    private ConversationTitle parseConversationTitle(String rawResult, String content) {
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(rawResult);
        if (StrUtil.isBlank(cleaned) || !JSONUtil.isTypeJSON(cleaned)) {
            return fallbackConversationTitle(content, memoryProperties.getTitleMaxLength());
        }
        JSONObject jsonObject = JSONUtil.parseObj(cleaned);
        int maxTitle = memoryProperties.getTitleMaxLength();
        ConversationTitle fallback = fallbackConversationTitle(content, maxTitle);
        String title = StrUtil.blankToDefault(jsonObject.getStr("title"), fallback.title());
        String description = StrUtil.blankToDefault(jsonObject.getStr("description"), fallback.description());
        return new ConversationTitle(limitText(title, maxTitle), limitText(description, DESCRIPTION_MAX_LENGTH));
    }

    /** 首次加载或写入时遇到缺失的元信息，均按相同的首问截断规则兜底。 */
    static ConversationTitle fallbackConversationTitle(String content, int maxTitle) {
        return new ConversationTitle(limitText(StrUtil.cleanBlank(content), maxTitle),
                limitText(content.trim(), DESCRIPTION_MAX_LENGTH));
    }

    /** 按 Unicode 码点截断，避免标题或描述末尾留下半个代理字符。 */
    private static String limitText(String text, int length) {
        if (text.codePointCount(0, text.length()) <= length) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, length));
    }

    record ConversationTitle(String title, String description) {
    }
}
