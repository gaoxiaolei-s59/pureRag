package org.puregxl.site.rag.core.retrieve.mcp;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolDispatcherTest {

    @Test
    void callBuildsJsonArgumentsFromToolSchemaAndReturnsToolResult() {
        AtomicReference<String> actualInput = new AtomicReference<>();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("rag_answer")
                        .description("基于 RAG 知识库生成简短答案")
                        .inputSchema("""
                                {"type":"object","properties":{"question":{"type":"string"},"topK":{"type":"integer"}}}
                                """)
                        .build();
            }

            @Override
            public String call(String toolInput) {
                actualInput.set(toolInput);
                return "{\"answer\":\"需要证明材料\"}";
            }
        };
        ObjectProvider<ToolCallbackProvider> providers = toolCallbackProviders(callback);
        McpToolDispatcher dispatcher = new McpToolDispatcher(providers, emptyLlmServiceProvider());

        String result = dispatcher.call("rag_answer", "缓考申请需要什么材料", 2);

        assertThat(result).isEqualTo("{\"answer\":\"需要证明材料\"}");
        assertThat(actualInput.get()).isEqualTo("{\"question\":\"缓考申请需要什么材料\",\"topK\":2}");
    }

    @Test
    void callUsesContextUserIdWhenToolSchemaRequiresUserId() {
        AtomicReference<String> actualInput = new AtomicReference<>();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("resume_list_by_user_id")
                        .description("根据用户ID查询该用户的简历列表")
                        .inputSchema("""
                                {"type":"object","properties":{"userId":{"type":"integer"}}}
                                """)
                        .build();
            }

            @Override
            public String call(String toolInput) {
                actualInput.set(toolInput);
                return "[{\"id\":1,\"userId\":42}]";
            }
        };
        ObjectProvider<ToolCallbackProvider> providers = toolCallbackProviders(callback);
        McpToolDispatcher dispatcher = new McpToolDispatcher(providers, emptyLlmServiceProvider());

        String result = dispatcher.call("resume_list_by_user_id", "请查询用户ID为 42 的简历", 3, "7");

        assertThat(result).isEqualTo("[{\"id\":1,\"userId\":42}]");
        assertThat(actualInput.get()).isEqualTo("{\"userId\":7}");
    }

    @Test
    void callUsesLlmExtractedArgumentsForSchemaSpecificToolParameters() {
        AtomicReference<String> actualInput = new AtomicReference<>();
        ToolCallback callback = new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("resume_list_by_user_id")
                        .description("根据用户ID查询该用户的简历列表")
                        .inputSchema("""
                                {"type":"object","properties":{"userId":{"type":"integer"}}}
                                """)
                        .build();
            }

            @Override
            public String call(String toolInput) {
                actualInput.set(toolInput);
                return "[{\"id\":1,\"userId\":42}]";
            }
        };
        LLMService llmService = mock(LLMService.class);
        when(llmService.chat(ArgumentMatchers.<ChatRequest>argThat(request -> request != null
                && request.getMessages() != null
                && request.getMessages().stream().anyMatch(message -> message.getContent().contains("用户ID为 42")))))
                .thenReturn("""
                        ```json
                        {"userId":42}
                        ```
                        """);
        McpToolDispatcher dispatcher = new McpToolDispatcher(toolCallbackProviders(callback), llmServiceProvider(llmService));
        IntentNode intentNode = IntentNode.builder()
                .mcpToolId("resume_list_by_user_id")
                .paramPromptTemplate("请从用户问题中抽取工具参数，只返回 JSON。")
                .build();

        String result = dispatcher.call(intentNode, "帮我查一下用户ID为 42 的简历", 3, "7");

        assertThat(result).isEqualTo("[{\"id\":1,\"userId\":42}]");
        assertThat(actualInput.get()).isEqualTo("{\"userId\":7}");
        verify(llmService).chat(ArgumentMatchers.<ChatRequest>argThat(request -> request != null
                && request.getTemperature().equals(0.0D)
                && request.getMessages().stream().anyMatch(message -> message.getContent().contains("resume_list_by_user_id"))));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ToolCallbackProvider> toolCallbackProviders(ToolCallback callback) {
        ObjectProvider<ToolCallbackProvider> providers = mock(ObjectProvider.class);
        when(providers.orderedStream()).thenReturn(Stream.of(ToolCallbackProvider.from(callback)));
        return providers;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<LLMService> emptyLlmServiceProvider() {
        ObjectProvider<LLMService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<LLMService> llmServiceProvider(LLMService llmService) {
        ObjectProvider<LLMService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(llmService);
        return provider;
    }
}
