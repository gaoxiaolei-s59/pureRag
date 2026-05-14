package org.puregxl.site.bootstrap;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

public class McpTest {

    private static final String DEFAULT_MCP_SERVER_BASE_URL = "http://localhost:8081";
    private static final String DEFAULT_MCP_SSE_ENDPOINT = "/sse";
    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn";
    private static final String DEFAULT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String DEFAULT_MODEL = "Qwen/Qwen2.5-7B-Instruct";

    @Test
    void queryUserResumeByMcpToolCalling() {
        String apiKey = "sk-rjtfqcpnhpzonswkebygmaqnqvibqcndgqxqfxghizuguthf";
        Assumptions.assumeTrue(hasText(apiKey), "请通过 -Dspring.ai.demo.api-key=你的Key 或环境变量 SILICONFLOW_API_KEY 配置 API Key");

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(property("mcp.server.base-url", DEFAULT_MCP_SERVER_BASE_URL))
                .sseEndpoint(property("mcp.server.sse-endpoint", DEFAULT_MCP_SSE_ENDPOINT))
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        try (McpSyncClient mcpClient = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .build()) {

            mcpClient.initialize();

            SyncMcpToolCallbackProvider toolCallbackProvider = new SyncMcpToolCallbackProvider(mcpClient);
            printAvailableTools(toolCallbackProvider);

            ChatClient chatClient = ChatClient.create(chatModel(apiKey));
            String userId = property("mcp.demo.user-id", "1");

            String answer = chatClient.prompt()
                    .system("""
                            你是简历查询助手。
                            当用户询问某个用户的简历时，必须调用 MCP 工具查询真实数据。
                            工具参数必须是 JSON：{"userId": 用户ID数字}。
                            回答时用中文，列出简历名称、状态、创建时间和更新时间；如果没有查到，明确说明没有简历。
                            """)
                    .user("请通过 MCP 服务查询用户ID为 " + userId + " 的简历，并整理成简短列表。")
                    .toolCallbacks(safeResumeToolCallback(toolCallbackProvider, userId))
                    .call()
                    .content();

            System.out.println("\n=== 大模型最终回答 ===");
            System.out.println(answer);
        }
    }

    private static void printAvailableTools(SyncMcpToolCallbackProvider toolCallbackProvider) {
        String toolNames = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .map(ToolCallback::getToolDefinition)
                .map(toolDefinition -> toolDefinition.name() + " - " + toolDefinition.description())
                .collect(Collectors.joining("\n"));

        System.out.println("=== MCP Tools ===");
        System.out.println(toolNames);
    }

    private static OpenAiChatModel chatModel(String apiKey) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(property("spring.ai.demo.base-url", DEFAULT_BASE_URL))
                .completionsPath(property("spring.ai.demo.completions-path", DEFAULT_COMPLETIONS_PATH))
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(property("spring.ai.demo.model", DEFAULT_MODEL))
                .temperature(0.1)
                .maxTokens(1024)
                .internalToolExecutionEnabled(true)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    private static String apiKey() {
        String fromProperty = System.getProperty("spring.ai.demo.api-key");
        if (hasText(fromProperty)) {
            return fromProperty;
        }
        String fromSiliconFlow = System.getenv("SILICONFLOW_API_KEY");
        if (hasText(fromSiliconFlow)) {
            return fromSiliconFlow;
        }
        return System.getenv("OPENAI_API_KEY");
    }

    private static String property(String key, String defaultValue) {
        String value = System.getProperty(key);
        return hasText(value) ? value : defaultValue;
    }

    private static RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(longProperty("spring.ai.demo.connect-timeout-seconds", 30)));
        requestFactory.setReadTimeout(Duration.ofSeconds(longProperty("spring.ai.demo.read-timeout-seconds", 180)));
        return RestClient.builder().requestFactory(requestFactory);
    }

    private static ToolCallback safeResumeToolCallback(SyncMcpToolCallbackProvider toolCallbackProvider, String userId) {
        ToolCallback delegate = Arrays.stream(toolCallbackProvider.getToolCallbacks())
                .filter(toolCallback -> "resume_list_by_user_id".equals(toolCallback.getToolDefinition().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("MCP 工具 resume_list_by_user_id 未注册"));

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                System.out.println("\n=== 模型发起 MCP 工具调用 ===");
                System.out.println("raw arguments=" + toolInput);

                String normalizedInput = "{\"userId\":" + normalizeUserId(userId) + "}";
                System.out.println("normalized arguments=" + normalizedInput);
                return delegate.call(normalizedInput);
            }
        };
    }

    private static long normalizeUserId(String userId) {
        try {
            long parsed = Long.parseLong(userId);
            return parsed > 0 ? parsed : 1L;
        } catch (NumberFormatException ex) {
            return 1L;
        }
    }

    private static long longProperty(String key, long defaultValue) {
        String value = System.getProperty(key);
        if (!hasText(value)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
