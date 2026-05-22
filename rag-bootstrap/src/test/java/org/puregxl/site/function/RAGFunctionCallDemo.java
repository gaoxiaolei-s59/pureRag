package org.puregxl.site.function;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class RAGFunctionCallDemo {

    private static final String API_KEY = "sk-rjtfqcpnhpzonswkebygmaqnqvibqcndgqxqfxghizuguthf";
    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final String MODEL = "deepseek-ai/DeepSeek-V3";

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .protocols(List.of(Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void main(String[] args) throws IOException {
        String userQuestion = "我还剩几天年假";

        JsonArray messages = new JsonArray();
        messages.add(message("system", """
                你是一个企业 HR 助手。
                当前登录用户 employee_id=E1001。
                当用户询问年假、调休、假期余额时，优先调用工具查询真实余额，再基于工具结果回答。
                """));
        messages.add(message("user", userQuestion));

        JsonObject firstRequest = new JsonObject();
        firstRequest.addProperty("model", MODEL);
        firstRequest.addProperty("temperature", 0.2);
        firstRequest.add("messages", messages);
        firstRequest.add("tools", buildTools());
        firstRequest.addProperty("tool_choice", "auto");

        JsonObject firstResponse = sendRequest(firstRequest);
        JsonObject assistantMessage = firstResponse
                .getAsJsonArray("choices")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("message");

        System.out.println("=== 首轮模型响应 ===");
        System.out.println(gson.toJson(assistantMessage));

        if (!assistantMessage.has("tool_calls") || assistantMessage.getAsJsonArray("tool_calls").isEmpty()) {
            System.out.println("=== 模型回答 ===");
            System.out.println(readContent(assistantMessage));
            return;
        }

        JsonArray toolCalls = assistantMessage.getAsJsonArray("tool_calls");
        messages.add(assistantToolMessage(assistantMessage, toolCalls));
        for (int i = 0; i < toolCalls.size(); i++) {
            JsonObject toolCall = toolCalls.get(i).getAsJsonObject();
            JsonObject function = toolCall.getAsJsonObject("function");
            String toolCallId = toolCall.get("id").getAsString();
            String functionName = function.get("name").getAsString();
            JsonObject functionArgs = parseJsonObject(function.get("arguments").getAsString());

            JsonObject functionResult = executeFunction(functionName, functionArgs);
            System.out.println("=== 本地函数执行结果 ===");
            System.out.println(functionName + "(" + gson.toJson(functionArgs) + ")");
            System.out.println(gson.toJson(functionResult));

            JsonObject toolMessage = new JsonObject();
            toolMessage.addProperty("role", "tool");
            toolMessage.addProperty("tool_call_id", toolCallId);
            toolMessage.addProperty("name", functionName);
            toolMessage.addProperty("content", gson.toJson(functionResult));
            messages.add(toolMessage);
        }

        JsonObject secondRequest = new JsonObject();
        secondRequest.addProperty("model", MODEL);
        secondRequest.addProperty("temperature", 0.2);
        secondRequest.add("messages", messages);

        JsonObject secondResponse = sendRequest(secondRequest);
        JsonObject finalMessage = secondResponse
                .getAsJsonArray("choices")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("message");

        System.out.println("=== 最终回答 ===");
        System.out.println(readContent(finalMessage));
    }

    /**
     * 定义模型可调用的函数。这里使用 OpenAI-compatible tools/function calling 格式。
     */
    private static JsonArray buildTools() {
        JsonArray tools = new JsonArray();

        JsonObject employeeId = new JsonObject();
        employeeId.addProperty("type", "string");
        employeeId.addProperty("description", "员工 ID，例如 E1001");

        JsonObject properties = new JsonObject();
        properties.add("employee_id", employeeId);

        JsonArray required = new JsonArray();
        required.add("employee_id");

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        parameters.add("required", required);

        JsonObject function = new JsonObject();
        function.addProperty("name", "get_annual_leave_balance");
        function.addProperty("description", "查询员工当前剩余年假天数、已使用天数和有效期");
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        tools.add(tool);

        return tools;
    }

    /**
     * 执行本地函数。真实业务里这里可以换成数据库、RAG 检索、MCP tool 或内部 HTTP 服务。
     */
    private static JsonObject executeFunction(String functionName, JsonObject arguments) {
        if ("get_annual_leave_balance".equals(functionName)) {
            String employeeId = arguments.has("employee_id")
                    ? arguments.get("employee_id").getAsString()
                    : "E1001";
            return getAnnualLeaveBalance(employeeId);
        }

        JsonObject error = new JsonObject();
        error.addProperty("error", "未知函数：" + functionName);
        return error;
    }

    private static JsonObject getAnnualLeaveBalance(String employeeId) {
        JsonObject result = new JsonObject();
        result.addProperty("employee_id", employeeId);
        result.addProperty("employee_name", "高晓雷");
        result.addProperty("total_days", 10);
        result.addProperty("used_days", 3);
        result.addProperty("remaining_days", 7);
        result.addProperty("expire_date", "2026-12-31");
        result.addProperty("data_source", "mock_hr_leave_balance");
        return result;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static JsonObject assistantToolMessage(JsonObject assistantMessage, JsonArray toolCalls) {
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        message.add("content", assistantMessage.has("content")
                ? assistantMessage.get("content")
                : JsonNull.INSTANCE);
        message.add("tool_calls", toolCalls);
        return message;
    }

    private static JsonObject parseJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return new JsonObject();
        }
        return gson.fromJson(json, JsonObject.class);
    }

    private static String readContent(JsonObject message) {
        if (!message.has("content") || message.get("content").isJsonNull()) {
            return "";
        }
        return message.get("content").getAsString();
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
