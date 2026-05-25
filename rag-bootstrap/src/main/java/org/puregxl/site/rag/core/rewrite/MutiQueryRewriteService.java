package org.puregxl.site.rag.core.rewrite;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MutiQueryRewriteService implements QueryRewriteService {

    private static final Gson GSON = new Gson();

    private final LLMService llmService;

    /**
     * 问题改写。
     * 这里的职责很单一：利用最近几轮历史把当前带指代的问题改写成一个更适合检索的问题，
     * 同时尽量拆出 0~3 个可选子问题。任何异常都降级回原问题，避免影响主问答链路。
     */
    @Override
    public RewriteResult rewrite(String userQuestion, List<ChatMessage> history) {
        if (StrUtil.isBlank(userQuestion)) {
            return fallback(userQuestion);
        }
        try {
            String response = llmService.chat(rewriteRequestBuilder(userQuestion, history));
            return parseOrFallback(userQuestion, response);
        } catch (Exception ex) {
            log.warn("[问题改写] 改写失败，回退原问题。question={}", userQuestion, ex);
            return fallback(userQuestion);
        }
    }

    /**
     * 构建改写提示词。
     * 约束模型必须返回严格 JSON，方便服务端稳定解析；
     * 如果当前问题本身已经完整清晰，要求模型直接原样返回 rewrittenQuestion。
     */
    public String rewriteRequestBuilder(String userQuestion, List<ChatMessage> history) {
        return """
                你是一个问题改写助手，负责把带上下文指代的用户问题改写成一个适合知识库检索的完整问题。
                请严格遵守以下规则：
                1. 只能补全上下文，不要改变用户原意。
                2. 如果当前问题已经清晰完整，rewrittenQuestion 直接返回原问题。
                3. subQuestions 最多返回 3 个，只有在确实能帮助检索时才拆分，否则返回空数组。
                4. 只返回 JSON，不要输出解释、Markdown 或代码块。
                5. JSON 格式必须是：
                {"rewrittenQuestion":"...","subQuestions":["...","..."]}

                历史对话：
                %s

                当前问题：
                %s
                """.formatted(buildHistoryText(history), userQuestion.trim());
    }

    private RewriteResult parseOrFallback(String userQuestion, String response) {
        if (StrUtil.isBlank(response)) {
            return fallback(userQuestion);
        }
        try {
            JsonObject jsonObject = JsonParser.parseString(extractJson(response)).getAsJsonObject();
            String rewrittenQuestion = readRewrittenQuestion(jsonObject, userQuestion);
            List<String> subQuestions = readSubQuestions(jsonObject);
            return RewriteResult.builder()
                    .rewrittenQuestion(rewrittenQuestion)
                    .subQuestions(subQuestions)
                    .build();
        } catch (IllegalStateException | JsonSyntaxException ex) {
            log.warn("[问题改写] 解析模型返回失败，回退原问题。response={}", response, ex);
            return fallback(userQuestion);
        }
    }

    private String extractJson(String response) {
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return trimmed;
        }
        return trimmed.substring(start, end + 1);
    }

    private String readRewrittenQuestion(JsonObject jsonObject, String originalQuestion) {
        if (jsonObject == null || !jsonObject.has("rewrittenQuestion") || jsonObject.get("rewrittenQuestion").isJsonNull()) {
            return originalQuestion;
        }
        String rewrittenQuestion = jsonObject.get("rewrittenQuestion").getAsString();
        return StrUtil.isBlank(rewrittenQuestion) ? originalQuestion : rewrittenQuestion.trim();
    }

    private List<String> readSubQuestions(JsonObject jsonObject) {
        if (jsonObject == null || !jsonObject.has("subQuestions") || !jsonObject.get("subQuestions").isJsonArray()) {
            return List.of();
        }
        JsonArray jsonArray = jsonObject.getAsJsonArray("subQuestions");
        List<String> result = new ArrayList<>();
        jsonArray.forEach(item -> {
            if (item != null && !item.isJsonNull()) {
                String value = item.getAsString();
                if (StrUtil.isNotBlank(value)) {
                    result.add(value.trim());
                }
            }
        });
        return result;
    }

    private String buildHistoryText(List<ChatMessage> history) {
        if (CollUtil.isEmpty(history)) {
            return "无历史对话";
        }
        StringBuilder builder = new StringBuilder();
        for (ChatMessage message : history) {
            if (message == null || StrUtil.isBlank(message.getContent())) {
                continue;
            }
            builder.append(message.getRole().name().toLowerCase())
                    .append(": ")
                    .append(message.getContent().trim())
                    .append("\n");
        }
        return builder.isEmpty() ? "无历史对话" : builder.toString().trim();
    }

    private RewriteResult fallback(String userQuestion) {
        return RewriteResult.builder()
                .rewrittenQuestion(StrUtil.blankToDefault(userQuestion, ""))
                .subQuestions(List.of())
                .build();
    }
}
