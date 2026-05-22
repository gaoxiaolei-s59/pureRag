package org.puregxl.site.context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


public class SlidingWindowCompressionDemo {

    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String API_KEY = "sk-rjtfqcpnhpzonswkebygmaqnqvibqcndgqxqfxghizuguthf";
    private static final String MODEL = Objects.requireNonNullElse(
            System.getenv("SILICONFLOW_CONTEXT_MODEL"),
            "Qwen/Qwen2.5-7B-Instruct"
    );

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .protocols(List.of(Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) {

    }


    /**
     * 有记忆模式：每次请求带上完整的历史消息
     */
    static void withMemoryDemo() throws IOException {
        List<JsonObject> history = new ArrayList<>();
        history.add(message("system", "你是一个电商客服助手，简洁回答用户问题。"));

        // 第 1 轮
        history.add(message("user", "iPhone 16 Pro 的退货政策是什么？"));
        String answer1 = chat(history);
        history.add(message("assistant", answer1));
        System.out.println("用户：iPhone 16 Pro 的退货政策是什么？");
        System.out.println("助手：" + answer1);

        // 第 2 轮：带上第 1 轮的历史，模型知道"它"指 iPhone 16 Pro
        history.add(message("user", "那它的保修期呢？"));
        String answer2 = chat(history);
        history.add(message("assistant", answer2));
        System.out.println("\n用户：那它的保修期呢？");
        System.out.println("助手：" + answer2);

        // 第 3 轮：继续追问
        history.add(message("user", "过了保修期维修大概多少钱？"));
        String answer3 = chat(history);
        System.out.println("\n用户：过了保修期维修大概多少钱？");
        System.out.println("助手：" + answer3);
    }

    public static JsonObject message(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }


    public static String chat(List<JsonObject> messages) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.addProperty("temperature", 0.1);
        body.addProperty("max_tokens", 512);
        JsonArray messagesArray = new JsonArray();
        for (JsonObject msg : messages) {
            messagesArray.add(msg);
        }
        body.add("messages", messagesArray);

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            JsonObject json = gson.fromJson(responseBody, JsonObject.class);

            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        }
    }


    /**
     * 发送 HTTP 请求
     */
    private static JsonObject sendRequest(JsonObject requestBody) throws IOException {
        RequestBody body = RequestBody.create(
                gson.toJson(requestBody),
                MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "RagTest/1.0 OkHttp")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                throw new IOException("请求失败：" + response + "，响应：" + responseBody);
            }
            return gson.fromJson(responseBody, JsonObject.class);
        }
    }
}
