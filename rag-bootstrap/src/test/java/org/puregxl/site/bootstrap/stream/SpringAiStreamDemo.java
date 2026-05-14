package org.puregxl.site.bootstrap.stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class SpringAiStreamDemo {

    private static final String DEFAULT_BASE_URL = "https://api.siliconflow.cn";
    private static final String DEFAULT_COMPLETIONS_PATH = "/v1/chat/completions";
    private static final String DEFAULT_MODEL = "Qwen/Qwen2.5-7B-Instruct";

    @Test
    void streamWithChatClient() {
        String apiKey = apiKey();
        Assumptions.assumeTrue(hasText(apiKey), "请通过 -Dspring.ai.demo.api-key=你的Key 或环境变量 SILICONFLOW_API_KEY 配置 API Key");

        ChatClient chatClient = ChatClient.create(chatModel(apiKey));

        System.out.println("===== ChatClient 流式输出 =====");
        chatClient.prompt()
                .system("你是一个简洁的 Java 和 Spring AI 助手。")
                .user("用三句话解释 Spring AI 的流式调用适合什么场景。")
                .stream()
                .content()
                .doOnNext(System.out::print)
                .blockLast(Duration.ofSeconds(120));
        System.out.println();
    }

    @Test
    void streamWithChatModel() {
        String apiKey = apiKey();
        Assumptions.assumeTrue(hasText(apiKey), "请通过 -Dspring.ai.demo.api-key=你的Key 或环境变量 SILICONFLOW_API_KEY 配置 API Key");

        ChatModel chatModel = chatModel(apiKey);
        Prompt prompt = new Prompt("写一个极简 SSE 接口的设计思路，要求分 3 点。");

        System.out.println("===== ChatModel 流式输出 =====");
        Flux<ChatResponse> responseFlux = chatModel.stream(prompt);
        responseFlux
                .map(response -> response.getResult().getOutput().getText())
                .filter(SpringAiStreamDemo::hasText)
                .doOnNext(System.out::print)
                .blockLast(Duration.ofSeconds(120));
        System.out.println();
    }

    private static ChatModel chatModel(String apiKey) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(property("spring.ai.demo.base-url", DEFAULT_BASE_URL))
                .completionsPath(property("spring.ai.demo.completions-path", DEFAULT_COMPLETIONS_PATH))
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(property("spring.ai.demo.model", DEFAULT_MODEL))
                .temperature(0.2)
                .maxTokens(512)
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
