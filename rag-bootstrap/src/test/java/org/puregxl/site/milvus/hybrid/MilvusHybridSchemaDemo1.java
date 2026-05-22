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
import okhttp3.*;

import java.io.IOException;
import java.util.*;

public class MilvusHybridSchemaDemo1 {

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
        public double dropRatioSearch = 0.2; // sparse(BM25) 搜索参数
        public int rrfK = 60;                // RRF 融合参数

        public List<String> outFields = List.of("text");
        public ConsistencyLevel consistencyLevel = ConsistencyLevel.BOUNDED;

        public static SearchConfig defaults() {
            return new SearchConfig();
        }
    }


    public static void main(String[] args) {
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri("http://localhost:19530")
                .build());

        createCollectionIfAbsentAndLoad(client);

        /**
         * 测试集合
         */
        String query = "缓考申请需要什么材料";

        SearchMode mode = SearchMode.HYBRID;

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

            schema.addField(AddFieldReq.builder()
                    .fieldName("text")
                    .dataType(DataType.VarChar)
                    .maxLength(8192)
                    .enableAnalyzer(true)
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
                    .indexType(IndexParam.IndexType.AUTOINDEX)
                    .metricType(IndexParam.MetricType.BM25)
                    .build();

            client.createIndex(CreateIndexReq.builder()
                    .collectionName(COLLECTION)
                    .indexParams(List.of(denseIndex, sparseIndex))
                    .build());

            List<JsonObject> rows = List.of(
                    buildRow("选课规则：学生应在每学期开学前两周内登录教务系统完成选课。选课结束后，原则上不再受理新增选课申请。"),
                    buildRow("课程注册说明：每学期正式上课前，学生需要在教务平台完成课程注册，逾期通常不能新增课程。"),
                    buildRow("退课规则：学生在开课后第一周内可申请退课，超过规定时间退课需经任课教师和学院审批。"),
                    buildRow("撤课说明：课程开课后一周内允许学生提交撤销选课申请，超期办理需经过学院审核。"),
                    buildRow("补选安排：因系统故障或特殊原因未完成选课的学生，可在补选阶段提交申请，补选时间一般为开学第二周。"),
                    buildRow("补选说明：未能在正式选课期间完成课程选择的学生，可以在后续补录阶段申请加选课程。"),
                    buildRow("课程容量说明：热门课程采用容量限制机制，选课人数达到上限后系统将不再接受新的选课请求。"),
                    buildRow("人数限制规则：当课程选修人数达到容量上限后，系统会停止新的报名申请。"),
                    buildRow("先修课程要求：部分专业课程设置先修要求，未修完指定基础课程的学生不能选修后续进阶课程。"),
                    buildRow("前置课程说明：某些高阶课程要求学生先完成基础课学习，否则无法进入后续课程学习阶段。"),
                    buildRow("考试安排：期末考试时间以教务处统一发布的考试通知为准，学生应至少提前 15 分钟进入考场。"),
                    buildRow("考试时间说明：学期末各课程考核时间由学校统一公布，考生需提前到达考场。"),
                    buildRow("考试证件要求：参加考试时，学生须携带本人学生证或校园卡，证件不齐者不得进入考场。"),
                    buildRow("入场凭证说明：考生进入考场时必须出示有效学生身份证明，例如学生证或校园卡。"),
                    buildRow("缓考申请：因生病或重大突发情况无法参加考试的学生，应在考试前提交缓考申请，并附相关证明材料。"),
                    buildRow("延期考试说明：学生如因疾病或紧急情况不能按时参加考试，可提前申请延期考试并提交证明。"),
                    buildRow("补考规定：期末考试不及格的学生可参加下一学期开学初组织的补考，补考机会一般仅限一次。"),
                    buildRow("重修与补测说明：课程考核未通过的学生，通常可以在下学期开学初参加一次补测。"),
                    buildRow("奖学金评定说明：奖学金评定主要参考学生成绩、综合表现和日常行为记录。"),
                    buildRow("宿舍管理规定：学生应遵守宿舍作息制度，严禁使用大功率违规电器。")
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
                Object text = r.getEntity() == null ? null : r.getEntity().get("text");
                System.out.println("  " + text);
            }
        }
    }

    /**
     * 构建一行数据：text + text_dense（text_sparse 由 BM25 Function 自动生成）
     */
    @SneakyThrows
    private static JsonObject buildRow(String text) {
        JsonObject row = new JsonObject();
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