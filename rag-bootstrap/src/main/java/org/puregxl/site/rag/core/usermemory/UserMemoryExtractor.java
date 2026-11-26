package org.puregxl.site.rag.core.usermemory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.util.LLMResponseCleaner;
import org.puregxl.site.rag.support.PromptTemplateLoader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 对已持久化的一条用户发言判定长期记忆变更。 */
@Component
@RequiredArgsConstructor
public class UserMemoryExtractor {
    public static final int MAX_FACTS = 32;
    public static final int MAX_FACT_CHARS = 256;
    public static final int MAX_INPUT_CHARS = 8000;
    private static final String PROMPT = "prompt/user-memory-extraction.txt";
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final LLMService model;
    private final PromptTemplateLoader templates;

    /**
     * 模型只判断本次可溯源的编辑。整批通过校验才返回新快照；调用方负责事务提交和游标推进。
     * 超长发言整体跳过，避免截断后把否定句或临时偏好误写成长期事实。
     */
    public List<String> extract(List<String> existingFacts, String userMessage) {
        if (userMessage == null || userMessage.isBlank()
                || userMessage.codePointCount(0, userMessage.length()) > MAX_INPUT_CHARS) {
            return List.copyOf(existingFacts);
        }
        try {
            String input = JSON.writeValueAsString(Map.of("existingFacts", existingFacts, "userMessage", userMessage));
            String raw = model.chat(ChatRequest.builder().temperature(0.0D)
                    .messages(List.of(ChatMessage.system(templates.load(PROMPT)), ChatMessage.user(input))).build());
            if (raw == null || raw.isBlank() || raw.length() > 128_000) {
                throw new IllegalStateException("长期记忆裁决正文为空或超长");
            }
            JsonNode output = JSON.readTree(LLMResponseCleaner.stripMarkdownCodeFence(raw));
            if (!output.isObject() || output.size() != 1 || !output.path("edits").isArray()
                    || output.get("edits").size() > MAX_FACTS) {
                throw new IllegalStateException("长期记忆裁决缺少有效的 edits 数组");
            }
            return applyEdits(existingFacts, userMessage, output.get("edits"));
        } catch (JsonProcessingException e) {
            // JSON 错误可能带有原始资料，不将其写入日志或向上游暴露。
            throw new IllegalStateException("长期记忆裁决 JSON 格式错误");
        }
    }

    /**
     * 所有索引都基于输入快照。先在副本中标记修改与删除，再追加新增，保持索引稳定。
     * 空间不足时只淘汰最早且本批没有编辑的事实。
     */
    private List<String> applyEdits(List<String> existing, String userMessage, JsonNode edits) {
        List<String> revised = new ArrayList<>(existing);
        Set<Integer> targeted = new HashSet<>();
        for (JsonNode edit : edits) {
            String quote = edit.path("quote").asText("");
            if (!edit.path("quote").isTextual() || quote.isBlank() || !userMessage.contains(quote)) {
                throw new IllegalStateException("长期记忆变更缺少本次用户原话作为依据");
            }
            switch (edit.path("op").asText("")) {
                case "clear" -> {
                    if (edits.size() != 1 || edit.size() != 2) {
                        throw new IllegalStateException("清空长期记忆必须单独提交");
                    }
                    return List.of();
                }
                case "add" -> {
                    requireFieldCount(edit, 3);
                    revised.add(factText(edit));
                }
                case "delete", "update" -> {
                    JsonNode index = edit.path("index");
                    if (!index.isInt() || index.intValue() < 1 || index.intValue() > existing.size()
                            || !targeted.add(index.intValue() - 1)) {
                        throw new IllegalStateException("长期记忆索引越界或重复编辑");
                    }
                    boolean delete = "delete".equals(edit.get("op").textValue());
                    requireFieldCount(edit, delete ? 3 : 4);
                    revised.set(index.intValue() - 1, delete ? null : factText(edit));
                }
                default -> throw new IllegalStateException("未知的长期记忆编辑类型");
            }
        }
        Set<String> facts = new LinkedHashSet<>(revised);
        facts.remove(null);
        Set<String> protectedFacts = new HashSet<>(revised.subList(existing.size(), revised.size()));
        targeted.forEach(index -> protectedFacts.add(revised.get(index)));
        for (String old : existing) {
            if (facts.size() <= MAX_FACTS) {
                break;
            }
            if (!protectedFacts.contains(old)) {
                facts.remove(old);
            }
        }
        return List.copyOf(facts);
    }

    private static String factText(JsonNode edit) {
        JsonNode value = edit.path("fact");
        String fact = value.asText("").strip();
        if (!value.isTextual() || fact.isBlank() || fact.codePointCount(0, fact.length()) > MAX_FACT_CHARS
                || fact.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("长期记忆事实为空、过长或包含控制字符");
        }
        return fact;
    }

    private static void requireFieldCount(JsonNode edit, int expected) {
        if (edit.size() != expected) {
            throw new IllegalStateException("长期记忆编辑的字段不符合约定");
        }
    }
}
