package org.puregxl.site.milvus.hybrid;


import cn.hutool.core.collection.CollUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq.Function;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.SneakyThrows;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.*;

public class MilvusHybridSchemaDemo2 {

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

        public List<String> outFields = List.of("file_id", "text");
        public ConsistencyLevel consistencyLevel = ConsistencyLevel.STRONG;

        public static SearchConfig defaults() {
            return new SearchConfig();
        }
    }

    public static void main(String[] args) {
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri("http://localhost:19530")
                .build());

        createCollectionIfAbsentAndLoad(client);

        String query = "缓考申请需要什么材料";

        SearchMode mode = SearchMode.SPARSE_ONLY;

        SearchConfig cfg = SearchConfig.defaults();

        SearchResp resp = runSearch(client, query, mode, cfg);

        printSearchResults(resp, mode);
    }

    /**
     * 创建 collection（若不存在），并确保 load
     */
    public static void createCollectionIfAbsentAndLoad(MilvusClientV2 client) {
        Boolean exists = client.hasCollection(
                HasCollectionReq.builder().collectionName(COLLECTION).build()
        );

        if (!Boolean.TRUE.equals(exists)) {
            // 1) Schema
            CreateCollectionReq.CollectionSchema schema = client.createSchema();

            schema.addField(AddFieldReq.builder()
                    .fieldName("id")
                    .dataType(DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(true)
                    .build());

            Map<String, Object> chineseAnalyzerParams = new HashMap<>();
            chineseAnalyzerParams.put("type", "chinese");

            schema.addField(AddFieldReq.builder()
                    .fieldName("text")
                    .dataType(DataType.VarChar)
                    .maxLength(8192)
                    .enableAnalyzer(true)
                    .analyzerParams(chineseAnalyzerParams)
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName("file_id")
                    .dataType(DataType.VarChar)
                    .maxLength(256)
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName("text_dense")
                    .dataType(DataType.FloatVector)
                    .dimension(4096) // 按你的 embedding 维度调整
                    .build());

            schema.addField(AddFieldReq.builder()
                    .fieldName("text_sparse")
                    .dataType(DataType.SparseFloatVector)
                    .build());

            // BM25 Function：text -> text_sparse
            schema.addFunction(Function.builder()
                    .functionType(FunctionType.BM25)
                    .name("text_bm25_emb")
                    .inputFieldNames(List.of("text"))
                    .outputFieldNames(List.of("text_sparse"))
                    .build());

            // 2) Create collection
            client.createCollection(CreateCollectionReq.builder()
                    .collectionName(COLLECTION)
                    .collectionSchema(schema)
                    .build());

            // 3) Index
            IndexParam denseIndex = IndexParam.builder()
                    .fieldName("text_dense")
                    .indexType(IndexParam.IndexType.AUTOINDEX)
                    .metricType(IndexParam.MetricType.COSINE)
                    .build();

            IndexParam sparseIndex = IndexParam.builder()
                    .fieldName("text_sparse")
                    .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                    .metricType(IndexParam.MetricType.BM25)
                    .extraParams(Map.of(
                            "inverted_index_algo", "DAAT_MAXSCORE",
                            "bm25_k1", 1.2,
                            "bm25_b", 0.75
                    ))
                    .build();

            client.createIndex(CreateIndexReq.builder()
                    .collectionName(COLLECTION)
                    .indexParams(List.of(denseIndex, sparseIndex))
                    .build());

            // 4) Insert demo data（只在首次创建时插入，避免重复）
            List<JsonObject> rows = Arrays.asList(
                    buildRow("course_selection_policy", "选课规则：学生应在每学期开学前两周内登录教务系统完成选课。"),
                    buildRow("course_withdraw_policy", "退课规则：学生在开课后第一周内可申请退课，超过时间需审批。"),
                    buildRow("exam_defer_policy", "缓考申请：因生病或重大突发情况无法参加考试的学生，应在考试前提交缓考申请，并附相关证明材料。"),
                    buildRow("exam_identity_policy", "考试证件要求：参加考试时，学生须携带本人学生证或校园卡。"),
                    buildRow("exam_makeup_policy", "补考规定：期末考试不及格的学生可参加下一学期开学初组织的补考。"),
                    buildRow("course_capacity_policy", "课程容量说明：热门课程达到人数上限后系统不再接受新的选课请求。")
            );

            InsertResp insertResp = client.insert(InsertReq.builder()
                    .collectionName(COLLECTION)
                    .data(rows)
                    .build());
            System.out.println("插入数据条数：" + insertResp.getInsertCnt());
        }

        // 无论是否已存在，都 load 一下，避免 “collection not loaded”
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(COLLECTION)
                .build());

        System.out.println("Collection 已就绪并加载：" + COLLECTION);
    }

    /**
     * 一个方法跑三种模式
     */
    @SneakyThrows
    public static SearchResp runSearch(MilvusClientV2 client,
                                       String queryText,
                                       SearchMode mode,
                                       SearchConfig cfg) {
        return switch (mode) {
            case DENSE_ONLY -> runDenseOnly(client, queryText, cfg);
            case SPARSE_ONLY -> runSparseOnly(client, queryText, cfg);
            default -> runHybrid(client, queryText, cfg);
        };
    }

    // ====== 1) dense-only：client.search(SearchReq) ======
    private static SearchResp runDenseOnly(MilvusClientV2 client,
                                           String queryText,
                                           SearchConfig cfg) throws IOException {
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
                                            SearchConfig cfg) {
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
                                        SearchConfig cfg) throws IOException {

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

    private static void printSearchResults(SearchResp resp, SearchMode mode) {
        System.out.println("\n===== Mode: " + mode + " =====");
        List<List<SearchResp.SearchResult>> results = resp.getSearchResults();
        for (List<SearchResp.SearchResult> oneQueryResults : results) {
            for (int i = 0; i < oneQueryResults.size(); i++) {
                SearchResp.SearchResult r = oneQueryResults.get(i);
                System.out.println("Top-" + (i + 1) + " score=" + r.getScore() + ", id=" + r.getId());
                Object fileId = r.getEntity() == null ? null : r.getEntity().get("file_id");
                Object text = r.getEntity() == null ? null : r.getEntity().get("text");
                System.out.println("  file_id: " + fileId);
                System.out.println("  " + text);
            }
        }
    }

    /**
     * 构建一行数据：text + text_dense（text_sparse 由 BM25 Function 自动生成）
     */
    @SneakyThrows
    private static JsonObject buildRow(String fileId, String text) {
        JsonObject row = new JsonObject();
        row.addProperty("file_id", fileId);
        row.addProperty("text", text);

        List<Float> denseVector = getEmbedding(text);
        JsonArray arr = new JsonArray();
        for (Float f : denseVector) arr.add(f);
        row.add("text_dense", arr);

        return row;
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
