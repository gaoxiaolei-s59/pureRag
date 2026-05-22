package org.puregxl.site.embedding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class EmbeddingClient {
    private static final String API_URL = "https://api.siliconflow.cn/v1/embeddings";

    private static final String Model = "Qwen/Qwen3-Embedding-8B";

    private final String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EmbeddingClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }


    public List<double[]> embed(List<String> texts) throws IOException, InterruptedException {
        HashMap<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", Model);
        requestBody.put("input", texts);
        requestBody.put("encoding_format", "float");

        String jsonBody = objectMapper.writeValueAsString(requestBody);


        // 发送 HTTP 请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();


        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("API 调用失败，状态码：" + response.statusCode()
                    + "，响应：" + response.body());
        }


        JsonNode root = objectMapper.readTree(response.body());
        JsonNode dataArray = root.get("data");
        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            throw new RuntimeException("API 响应中没有 embedding 数据，响应：" + response.body());
        }

        ArrayList<double[]> embeddings = new ArrayList<>(texts.size());

        for (JsonNode jsonNode : dataArray) {
            JsonNode embeddingNode = jsonNode.get("embedding");
            if (embeddingNode == null || !embeddingNode.isArray()) {
                throw new RuntimeException("API 响应中的 embedding 格式不正确，响应：" + response.body());
            }
            double[] vector = new double[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size() ; i++) {
                vector[i] = embeddingNode.get(i).asDouble();
            }
            embeddings.add(vector);
        }
        return embeddings;
    }


    public double[] embed(String text) throws IOException, InterruptedException {
        return embed(List.of(text)).get(0);
    }
}
