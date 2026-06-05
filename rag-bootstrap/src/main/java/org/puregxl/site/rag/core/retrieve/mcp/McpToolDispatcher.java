package org.puregxl.site.rag.core.retrieve.mcp;

import cn.hutool.core.util.StrUtil;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.util.LLMResponseCleaner;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP 工具调度器。
 * <p>
 * 这里隔离 Spring AI MCP Client 的 ToolCallback 细节，检索主流程只需要传入工具名、
 * 子问题和 TopK。参数构建优先基于 MCP 工具声明的 inputSchema 做稳定映射，避免把协议对象泄漏到
 * RetrievalEngine 中。
 */
@Slf4j
@Service
public class McpToolDispatcher {

    private static final Pattern POSITIVE_INTEGER_PATTERN = Pattern.compile("\\d+");
    private static final double PARAM_EXTRACT_TEMPERATURE = 0.0D;

    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    private final ObjectProvider<LLMService> llmServiceProvider;
    private final Gson gson = new Gson();

    public McpToolDispatcher(ObjectProvider<ToolCallbackProvider> toolCallbackProviders,
                             ObjectProvider<LLMService> llmServiceProvider) {
        this.toolCallbackProviders = toolCallbackProviders;
        this.llmServiceProvider = llmServiceProvider;
    }

    /**
     * 根据 MCP 工具名调用对应 ToolCallback。
     * <p>
     * Spring AI MCP Client 会把远端 MCP Server 的工具转换为 ToolCallback；本方法只负责查找工具、
     * 构建 JSON 参数和异常降级。单个工具失败时返回空字符串，避免拖垮整次 RAG 问答。
     */
    public String call(IntentNode intentNode, String subQuestion, Integer topK) {
        if (intentNode == null) {
            return "";
        }
        return call(intentNode.getMcpToolId(), subQuestion, topK, intentNode.getParamPromptTemplate());
    }

    public String call(String toolName, String subQuestion, Integer topK) {
        return call(toolName, subQuestion, topK, null);
    }

    private String call(String toolName, String subQuestion, Integer topK, String paramPromptTemplate) {
        if (StrUtil.isBlank(toolName) || StrUtil.isBlank(subQuestion)) {
            return "";
        }

        Optional<ToolCallback> toolCallback = findToolCallback(toolName.trim());
        if (toolCallback.isEmpty()) {
            log.warn("MCP 工具未注册或客户端未初始化，toolName：{}", toolName);
            return "";
        }

        ToolCallback callback = toolCallback.get();
        String argumentsJson = gson.toJson(buildArguments(
                toolName.trim(),
                callback.getToolDefinition(),
                subQuestion.trim(),
                topK,
                paramPromptTemplate
        ));
        try {
            return StrUtil.blankToDefault(callback.call(argumentsJson), "");
        } catch (Exception ex) {
            log.error("MCP 工具调用失败，toolName：{}，arguments：{}", toolName, argumentsJson, ex);
            return "";
        }
    }

    private Optional<ToolCallback> findToolCallback(String toolName) {
        return toolCallbackProviders.orderedStream()
                .filter(Objects::nonNull)
                .flatMap(provider -> Arrays.stream(safeToolCallbacks(provider)))
                .filter(Objects::nonNull)
                .filter(callback -> callback.getToolDefinition() != null)
                .filter(callback -> toolName.equals(callback.getToolDefinition().name()))
                .findFirst();
    }

    private ToolCallback[] safeToolCallbacks(ToolCallbackProvider provider) {
        try {
            ToolCallback[] callbacks = provider.getToolCallbacks();
            return callbacks == null ? new ToolCallback[0] : callbacks;
        } catch (Exception ex) {
            log.error("读取 MCP ToolCallback 列表失败，跳过当前 provider", ex);
            return new ToolCallback[0];
        }
    }

    private Map<String, Object> buildArguments(String toolName,
                                               ToolDefinition toolDefinition,
                                               String subQuestion,
                                               Integer topK,
                                               String paramPromptTemplate) {
        String inputSchema = toolDefinition == null ? "" : StrUtil.blankToDefault(toolDefinition.inputSchema(), "");
        Map<String, Object> llmArguments = extractArgumentsWithLlm(toolName, toolDefinition, inputSchema, subQuestion, topK, paramPromptTemplate);
        if (!llmArguments.isEmpty()) {
            return llmArguments;
        }

        Map<String, Object> arguments = new LinkedHashMap<>();

        // 常见 RAG 工具参数：query/question 承载子问题，topK 使用意图节点配置或检索默认值。
        if (hasSchemaProperty(inputSchema, "query")) {
            arguments.put("query", subQuestion);
        }
        if (hasSchemaProperty(inputSchema, "question")) {
            arguments.put("question", subQuestion);
        }
        if (hasSchemaProperty(inputSchema, "topK")) {
            arguments.put("topK", normalizeTopK(topK));
        }

        // 当前 rag-mcp 中的简历工具需要 userId，先从子问题中抽取显式数字。
        if (hasSchemaProperty(inputSchema, "userId")) {
            firstPositiveLong(subQuestion).ifPresent(userId -> arguments.put("userId", userId));
        }

        if (arguments.isEmpty()) {
            arguments.put("question", subQuestion);
        }
        return arguments;
    }

    private Map<String, Object> extractArgumentsWithLlm(String toolName,
                                                        ToolDefinition toolDefinition,
                                                        String inputSchema,
                                                        String subQuestion,
                                                        Integer topK,
                                                        String paramPromptTemplate) {
        LLMService llmService = llmServiceProvider.getIfAvailable();
        if (llmService == null || !shouldUseLlmExtraction(inputSchema, paramPromptTemplate)) {
            return Map.of();
        }

        try {
            String response = llmService.chat(ChatRequest.builder()
                    .messages(List.of(ChatMessage.user(buildParamExtractPrompt(
                            toolName,
                            toolDefinition,
                            inputSchema,
                            subQuestion,
                            normalizeTopK(topK),
                            paramPromptTemplate
                    ))))
                    .temperature(PARAM_EXTRACT_TEMPERATURE)
                    .build());
            return parseLlmArguments(response, inputSchema);
        } catch (Exception ex) {
            log.warn("MCP 参数抽取失败，回退规则参数构建，toolName：{}", toolName, ex);
            return Map.of();
        }
    }

    private boolean shouldUseLlmExtraction(String inputSchema, String paramPromptTemplate) {
        return StrUtil.isNotBlank(paramPromptTemplate) || hasSchemaProperty(inputSchema, "userId");
    }

    private String buildParamExtractPrompt(String toolName,
                                           ToolDefinition toolDefinition,
                                           String inputSchema,
                                           String subQuestion,
                                           Integer topK,
                                           String paramPromptTemplate) {
        String template = StrUtil.blankToDefault(paramPromptTemplate, """
                请从用户问题中抽取 MCP 工具调用参数，只返回 JSON 对象，不要解释，不要 Markdown。
                如果无法确定某个可选参数，可以省略该字段；如果无法确定必填参数，返回空 JSON：{}。
                """);
        return """
                %s

                工具名称：%s
                工具描述：%s
                工具参数 JSON Schema：%s
                用户问题：%s
                默认 topK：%s
                """.formatted(
                template.trim(),
                toolName,
                toolDefinition == null ? "" : StrUtil.blankToDefault(toolDefinition.description(), ""),
                StrUtil.blankToDefault(inputSchema, "{}"),
                subQuestion,
                topK
        );
    }

    private Map<String, Object> parseLlmArguments(String response, String inputSchema) {
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(response);
        if (StrUtil.isBlank(cleaned)) {
            return Map.of();
        }

        JsonElement jsonElement = JsonParser.parseString(cleaned);
        if (!jsonElement.isJsonObject()) {
            return Map.of();
        }

        Set<String> allowedProperties = schemaProperties(inputSchema);
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            if (allowedProperties.isEmpty() || allowedProperties.contains(entry.getKey())) {
                arguments.put(entry.getKey(), toJavaValue(entry.getValue()));
            }
        }
        return arguments;
    }

    private Object toJavaValue(JsonElement jsonElement) {
        if (jsonElement == null || jsonElement.isJsonNull()) {
            return null;
        }
        if (!jsonElement.isJsonPrimitive()) {
            return gson.fromJson(jsonElement, Object.class);
        }

        JsonPrimitive primitive = jsonElement.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        if (!primitive.isNumber()) {
            return gson.fromJson(jsonElement, Object.class);
        }

        BigDecimal number = primitive.getAsBigDecimal();
        BigDecimal stripped = number.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.longValueExact();
        }
        return number.doubleValue();
    }

    private Set<String> schemaProperties(String inputSchema) {
        if (StrUtil.isBlank(inputSchema)) {
            return Set.of();
        }
        try {
            JsonObject schema = JsonParser.parseString(inputSchema).getAsJsonObject();
            JsonObject properties = schema.getAsJsonObject("properties");
            return properties == null ? Set.of() : new LinkedHashSet<>(properties.keySet());
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private boolean hasSchemaProperty(String inputSchema, String propertyName) {
        return StrUtil.isNotBlank(inputSchema) && inputSchema.contains("\"" + propertyName + "\"");
    }

    private Integer normalizeTopK(Integer topK) {
        if (topK == null || topK <= 0) {
            return 3;
        }
        return Math.min(topK, 10);
    }

    private Optional<Long> firstPositiveLong(String text) {
        Matcher matcher = POSITIVE_INTEGER_PATTERN.matcher(StrUtil.blankToDefault(text, ""));
        while (matcher.find()) {
            try {
                long value = Long.parseLong(matcher.group());
                if (value > 0) {
                    return Optional.of(value);
                }
            } catch (NumberFormatException ignored) {
                // 超长数字不作为有效 userId，继续尝试后续片段。
            }
        }
        return Optional.empty();
    }
}
