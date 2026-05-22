package org.puregxl.site.milvus.combine;

import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.SneakyThrows;
import okhttp3.*;
import org.puregxl.site.milvus.hybrid.MilvusHybridSchemaDemo2;

import java.io.IOException;
import java.util.*;

public class ComBineDemo {


    private static final String COLLECTION = "customer_service_hybrid";

    private static final String SILICONFLOW_API_KEY = "sk-rjtfqcpnhpzonswkebygmaqnqvibqcndgqxqfxghizuguthf";
    private static final String EMBEDDING_URL = "https://api.siliconflow.cn/v1/embeddings";
    private static final String EMBEDDING_MODEL = "Qwen/Qwen3-Embedding-8B";

    private static final Gson GSON = new Gson();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();

    /**
     * 三种检索模式
     */
    public enum SearchMode {
        DENSE_ONLY,     // 只跑 dense（纯向量）
        SPARSE_ONLY,    // 只跑 sparse（纯 BM25）
        HYBRID          // dense + sparse 混合
    }

    /**
     * 参数配置（你也可以按需要继续扩展）
     */
    public static class SearchConfig {
        public int denseRecallTopK = 20;     // dense 召回
        public int sparseRecallTopK = 20;    // sparse 召回
        public int finalTopK = 8;            // 最终返回（单路时也用它）

        public int nprobe = 16;              // dense 搜索参数
        public double dropRatioSearch = 0.0; // sparse(BM25) 搜索参数
        public int rrfK = 60;                // RRF 融合参数

        public List<String> outFields = List.of("text");
        public ConsistencyLevel consistencyLevel = ConsistencyLevel.STRONG;

        public static MilvusHybridSchemaDemo2.SearchConfig defaults() {
            return new MilvusHybridSchemaDemo2.SearchConfig();
        }
    }


    /**
     * 一个方法跑三种模式
     */
    @SneakyThrows
    public static SearchResp runSearch(MilvusClientV2 client,
                                       String queryText,
                                       MilvusHybridSchemaDemo2.SearchMode mode,
                                       MilvusHybridSchemaDemo2.SearchConfig cfg) {
        return switch (mode) {
            case DENSE_ONLY -> runDenseOnly(client, queryText, cfg);
            case SPARSE_ONLY -> runSparseOnly(client, queryText, cfg);
            default -> runHybrid(client, queryText, cfg);
        };
    }

    // ====== 1) dense-only：client.search(SearchReq) ======
    private static SearchResp runDenseOnly(MilvusClientV2 client,
                                           String queryText,
                                           MilvusHybridSchemaDemo2.SearchConfig cfg) throws IOException {
        List<Float> queryDenseVector = getEmbedding(queryText);

        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("metric_type", "COSINE");
        searchParams.put("nprobe", cfg.nprobe);

        return client.search(SearchReq.builder()
                .collectionName(COLLECTION)
                .annsField("text_dense")
                .data(Collections.singletonList(new FloatVec(queryDenseVector)))
                .topK(cfg.finalTopK)
                .outputFields(cfg.outFields)
                .searchParams(searchParams)
                .consistencyLevel(cfg.consistencyLevel)
                .build());
    }

    // ====== 2) sparse-only(BM25)：client.search(SearchReq) ======
    private static SearchResp runSparseOnly(MilvusClientV2 client,
                                            String queryText,
                                            MilvusHybridSchemaDemo2.SearchConfig cfg) {
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("metric_type", "BM25");
        searchParams.put("drop_ratio_search", cfg.dropRatioSearch);

        return client.search(SearchReq.builder()
                .collectionName(COLLECTION)
                .annsField("text_sparse")
                // 直接传原文，让 Milvus 做 analyzer + BM25
                .data(Collections.singletonList(new EmbeddedText(queryText)))
                .topK(cfg.finalTopK)
                .outputFields(cfg.outFields)
                .searchParams(searchParams)
                .consistencyLevel(cfg.consistencyLevel)
                .build());
    }

    // ====== 3) hybrid：client.hybridSearch(HybridSearchReq) ======
    private static SearchResp runHybrid(MilvusClientV2 client,
                                        String queryText,
                                        MilvusHybridSchemaDemo2.SearchConfig cfg) throws IOException {

        // dense AnnSearchReq
        List<Float> queryDenseVector = getEmbedding(queryText);
        AnnSearchReq denseReq = AnnSearchReq.builder()
                .vectorFieldName("text_dense")
                .vectors(Collections.singletonList(new FloatVec(queryDenseVector)))
                .params("{\"nprobe\": " + cfg.nprobe + "}")
                .topK(cfg.denseRecallTopK)
                .build();

        // sparse AnnSearchReq (BM25)
        AnnSearchReq sparseReq = AnnSearchReq.builder()
                .vectorFieldName("text_sparse")
                .vectors(Collections.singletonList(new EmbeddedText(queryText)))
                .params("{\"drop_ratio_search\": " + cfg.dropRatioSearch + "}")
                .topK(cfg.sparseRecallTopK)
                .build();

        HybridSearchReq hybridReq = HybridSearchReq.builder()
                .collectionName(COLLECTION)
                .searchRequests(List.of(denseReq, sparseReq))
                .ranker(new RRFRanker(cfg.rrfK))
                .topK(cfg.finalTopK)
                .consistencyLevel(cfg.consistencyLevel)
                .outFields(cfg.outFields)
                .build();

        return client.hybridSearch(hybridReq);
    }



    /**
     * 调用 SiliconFlow Embedding API，生成单条向量
     */
    private static List<Float> getEmbedding(String text) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", EMBEDDING_MODEL);
        requestBody.add("input", GSON.toJsonTree(List.of(text)));

        Request request = new Request.Builder()
                .url(EMBEDDING_URL)
                .addHeader("Authorization", "Bearer " + SILICONFLOW_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                        GSON.toJson(requestBody),
                        MediaType.parse("application/json")
                ))
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                throw new IOException("Embedding API 调用失败 http=" + response.code() + ", body=" + body);
            }

            JsonObject json = GSON.fromJson(body, JsonObject.class);
            JsonArray dataArray = json.getAsJsonArray("data");
            if (CollUtil.isEmpty(dataArray)) {
                throw new IOException("Embedding API 返回 data 为空，原始响应: " + body);
            }

            JsonArray embeddingArray = dataArray.get(0).getAsJsonObject().getAsJsonArray("embedding");
            if (embeddingArray == null) {
                throw new IOException("Embedding API 返回 embedding 为空，原始响应: " + body);
            }

            List<Float> vector = new ArrayList<>(embeddingArray.size());
            for (int i = 0; i < embeddingArray.size(); i++) {
                vector.add(embeddingArray.get(i).getAsFloat());
            }
            return vector;
        }
    }


}
