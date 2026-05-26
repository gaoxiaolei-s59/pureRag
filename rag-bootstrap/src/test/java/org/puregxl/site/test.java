package org.puregxl.site;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class test {
    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String API_KEY = "sk-rjtfqcpnhpzonswkebygmaqnqvibqcndgqxqfxghizuguthf";
    private static final String MODEL = "Qwen/Qwen2.5-7B-Instruct";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private final Gson GSON = new Gson();

    @Test
    void test() throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", MODEL);
        requestBody.addProperty("temperature", 0.2);
        requestBody.addProperty("max_tokens", 512);

        JsonArray messages = new JsonArray();
        messages.add(message("system", "你是一个简洁、准确的 Java 学习助手。"));
        messages.add(message("user", "用一句话解释什么是 RAG。"));
        requestBody.add("messages", messages);

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        GSON.toJson(requestBody),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                throw new IOException("API 调用失败，状态码：" + response.code() + "，响应：" + body);
            }

            JsonObject responseJson = GSON.fromJson(body, JsonObject.class);
            System.out.println(responseJson);
            String content = responseJson
                    .getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            System.out.println("模型回答：");
            System.out.println(content);
            System.out.println("模型token：");
            JsonObject usage = responseJson.getAsJsonObject("usage");

            int promptTokens = usage.get("prompt_tokens").getAsInt();
            int completionTokens = usage.get("completion_tokens").getAsInt();
            int totalTokens = usage.get("total_tokens").getAsInt();

            JsonObject completionTokensDetails = usage.getAsJsonObject("completion_tokens_details");
            int reasoningTokens = completionTokensDetails.get("reasoning_tokens").getAsInt();

            JsonObject promptTokensDetails = usage.getAsJsonObject("prompt_tokens_details");
            int cachedTokens = promptTokensDetails.get("cached_tokens").getAsInt();

            System.out.println("prompt_tokens = " + promptTokens);
            System.out.println("completion_tokens = " + completionTokens);
            System.out.println("total_tokens = " + totalTokens);
            System.out.println("reasoning_tokens = " + reasoningTokens);
            System.out.println("cached_tokens = " + cachedTokens);
            System.out.println();
        }
    }

    private JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);

        return message;
    }
}
