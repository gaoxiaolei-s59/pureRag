package org.puregxl.site.bootstrap.evally;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class RAGEvaluator {

    private static final String CHAT_API_URL = property("eval.chat.url", "https://api.siliconflow.cn/v1/chat/completions");
    private static final String EMBEDDING_API_URL = property("eval.embedding.url", "https://api.siliconflow.cn/v1/embeddings");
    private static final String API_KEY = property("eval.apiKey", "sk-rjtfqcpnhpzonswkebygmaqnqvibqcndgqxqfxghizuguthf");
    private static final String JUDGE_MODEL = property("eval.judge.model", "deepseek-ai/DeepSeek-V3");
    private static final String ANSWER_MODEL = property("eval.answer.model", "Qwen/Qwen2.5-7B-Instruct");
    private static final String EMBEDDING_MODEL = property("eval.embedding.model", "Qwen/Qwen3-Embedding-8B");
    private static final String MILVUS_URI = property("eval.milvus.uri", "http://localhost:19530");
    private static final String COLLECTION_NAME = property("eval.collection", "rag_evaluator_real_30");
    private static final int EMBEDDING_DIMENSION = Integer.parseInt(property("eval.embedding.dimension", "4096"));
    private static final int TOP_K = Integer.parseInt(property("eval.topK", "1"));
    private static final double NO_ANSWER_SCORE_THRESHOLD = Double.parseDouble(property("eval.noAnswerScoreThreshold", "0.55"));
    private static final double STABLE_AVERAGE_SCORE = Double.parseDouble(property("eval.stableAverageScore", "4.8"));
    private static final Gson gson = new Gson();
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build();

    private static String property(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }

    // 评测数据结构
    static class EvalCase {
        String query;            // 用户问题
        String expectedAnswer;   // 标准答案
        List<String> relevantChunkIds;  // 正确答案对应的 chunk ID
        String intent;           // 意图类别

        EvalCase(String query, String expectedAnswer, List<String> relevantChunkIds, String intent) {
            this.query = query;
            this.expectedAnswer = expectedAnswer;
            this.relevantChunkIds = relevantChunkIds;
            this.intent = intent;
        }
    }

    static class KnowledgeChunk {
        String chunkId;
        String title;
        String content;
        String intent;

        KnowledgeChunk(String chunkId, String title, String content, String intent) {
            this.chunkId = chunkId;
            this.title = title;
            this.content = content;
            this.intent = intent;
        }
    }

    // 评分结果
    static class ScoreResult {
        int score;       // 1-5 分
        String reason;   // 评分理由
    }

    // 单条评测结果
    static class EvalResult {
        EvalCase evalCase;
        List<String> retrievedChunkIds;  // 实际检索到的 chunk ID 列表
        List<RetrievedChunk> retrievedChunks; // 实际检索到的 chunk 内容
        String actualAnswer;             // 模型实际生成的答案
        boolean hit;                     // 检索是否命中
        double reciprocalRank;           // 倒数排名
        ScoreResult faithfulness;        // 忠实度评分
        ScoreResult relevancy;           // 相关性评分
        ScoreResult correctness;         // 正确率评分
        boolean fallbackAnswer;          // 是否做了无答案兜底
        double topScore;                 // Top-1 相似度分数
    }

    static class RetrievedChunk {
        String chunkId;
        String queryText;
        String expectedAnswer;
        String content;
        double score;

        RetrievedChunk(String chunkId, String queryText, String expectedAnswer, String content, double score) {
            this.chunkId = chunkId;
            this.queryText = queryText;
            this.expectedAnswer = expectedAnswer;
            this.content = content;
            this.score = score;
        }
    }

    // 构建真实知识库，向量数据库只写入这些条款，不写入评测问题
    static List<KnowledgeChunk> buildKnowledgeBase() {
        return List.of(
                new KnowledgeChunk("kb_exam_defer", "缓考申请",
                        "因生病或重大突发情况无法参加考试的学生，应在考试前提交缓考申请，并附医院诊断证明、病历或重大突发情况说明等相关证明材料。",
                        "exam_defer"),
                new KnowledgeChunk("kb_course_makeup_select", "补选安排",
                        "因系统故障或特殊原因未完成选课的学生，可在补选阶段提交申请，补选时间一般为开学第二周。",
                        "course_makeup_select"),
                new KnowledgeChunk("kb_course_withdraw", "退课规则",
                        "学生在开课后第一周内可申请退课，超过规定时间退课需经任课教师和学院审批。",
                        "course_withdraw"),
                new KnowledgeChunk("kb_exam_entry", "考试入场",
                        "期末考试时间以教务处统一发布的考试通知为准，学生应至少提前 15 分钟进入考场；参加考试时须携带本人学生证或校园卡，证件不齐者不得进入考场。",
                        "exam_entry"),
                new KnowledgeChunk("kb_makeup_exam", "补考规定",
                        "期末考试不及格的学生可参加下一学期开学初组织的补考，补考机会一般仅限一次。",
                        "makeup_exam"),
                new KnowledgeChunk("kb_course_capacity", "课程容量",
                        "热门课程采用容量限制机制，选课人数达到上限后系统将不再接受新的选课请求。",
                        "course_capacity"),
                new KnowledgeChunk("kb_prerequisite", "先修课程",
                        "部分专业课程设置先修要求，未修完指定基础课程的学生不能选修后续进阶课程。",
                        "prerequisite"),
                new KnowledgeChunk("kb_course_selection_window", "选课时间",
                        "学生应在每学期开学前两周内登录教务系统完成选课，选课结束后原则上不再受理新增选课申请。",
                        "course_selection_window"),
                new KnowledgeChunk("kb_course_score", "成绩构成",
                        "课程最终成绩由平时成绩、实验成绩和期末考试成绩综合评定，具体比例以课程大纲为准。",
                        "course_score"),
                new KnowledgeChunk("kb_exam_violation", "考试违纪",
                        "考试过程中不得携带与考试无关的电子设备或资料；违反考场纪律的学生，将按照学校考试违纪处理办法处理。",
                        "exam_violation"),
                new KnowledgeChunk("kb_lab_safety", "实验室安全",
                        "进入实验室前应完成安全培训，实验过程中须穿戴必要防护用品，并遵守实验室设备使用规范。",
                        "lab_safety"),
                new KnowledgeChunk("kb_retake_course", "重修规则",
                        "课程考核不合格且补考仍未通过的学生，应按学院教学安排申请课程重修，重修课程成绩按学校成绩管理办法记录。",
                        "retake_course"),
                new KnowledgeChunk("kb_course_add_after_deadline", "新增选课限制",
                        "选课结束后原则上不再受理新增选课申请；确因培养方案调整需要新增课程的，应由学院统一审核后报教务处处理。",
                        "course_add_after_deadline"),
                new KnowledgeChunk("kb_exam_absence", "考试缺考",
                        "未办理缓考手续且无故不参加考试的学生，该课程考试成绩按缺考处理。",
                        "exam_absence"),
                new KnowledgeChunk("kb_lab_course_score", "实验课程成绩",
                        "实验类课程的成绩由实验预习、实验操作、实验报告和课程考核等部分综合评定，具体比例以实验课程大纲为准。",
                        "lab_course_score"),
                new KnowledgeChunk("kb_course_waitlist", "课程候补",
                        "部分热门课程可设置候补名单，候补仅表示有空余名额时按规则递补，不代表学生已经完成正式选课。",
                        "course_waitlist")
        );
    }

    // 构建评测数据集，30 条 query 与知识库条款分离
    static List<EvalCase> buildEvalDataset() {
        List<EvalCase> cases = List.of(
                new EvalCase("我临时发烧，明天考试去不了，光跟老师说一声够不够？", "不够。因生病无法参加考试，应在考试前提交缓考申请，并附医院诊断证明或病历等证明材料。", List.of("kb_exam_defer"), "exam_defer"),
                new EvalCase("突然家里出了事，缓考是不是也要材料？", "需要。重大突发情况申请缓考，应在考试前提交申请，并附重大突发情况说明等相关证明材料。", List.of("kb_exam_defer"), "exam_defer"),
                new EvalCase("没办缓考直接缺考，会不会按缓考处理？", "不会。未办理缓考手续且无故不参加考试的学生，该课程考试成绩按缺考处理。", List.of("kb_exam_absence"), "exam_absence"),
                new EvalCase("系统崩了没选上课，这算补选还是新增选课？", "因系统故障未完成选课的学生，可在补选阶段提交申请，补选时间一般为开学第二周。", List.of("kb_course_makeup_select"), "course_makeup_select"),
                new EvalCase("不是系统问题，只是我忘了选课，结束后还能随便加吗？", "选课结束后原则上不再受理新增选课申请；确因培养方案调整需要新增课程的，应由学院统一审核后报教务处处理。", List.of("kb_course_add_after_deadline"), "course_add_after_deadline"),
                new EvalCase("开学第二周那个补救选课，是所有人都能补吗？", "不是。补选主要适用于因系统故障或特殊原因未完成选课的学生，补选时间一般为开学第二周。", List.of("kb_course_makeup_select"), "course_makeup_select"),
                new EvalCase("已经上了两周课，想退课还用不用老师和学院同意？", "需要。超过开课后第一周的退课时间，退课需经任课教师和学院审批。", List.of("kb_course_withdraw"), "course_withdraw"),
                new EvalCase("第一周退课和选课结束后加课是不是一回事？", "不是。开课后第一周内可申请退课；选课结束后原则上不再受理新增选课申请。", List.of("kb_course_withdraw", "kb_course_add_after_deadline"), "cross_policy"),
                new EvalCase("退课超过时间了，是只找任课老师就行吗？", "不行。超过规定时间退课需经任课教师和学院审批。", List.of("kb_course_withdraw"), "course_withdraw"),
                new EvalCase("考试当天证件没带但人提前到了，可以进场吗？", "不可以。参加考试须携带本人学生证或校园卡，证件不齐者不得进入考场。", List.of("kb_exam_entry"), "exam_entry"),
                new EvalCase("考试时间听同学说和教务处通知不一样，以哪个为准？", "期末考试时间以教务处统一发布的考试通知为准。", List.of("kb_exam_entry"), "exam_entry"),
                new EvalCase("考试提前 5 分钟到，证件也齐，符合要求吗？", "不符合。学生应至少提前 15 分钟进入考场，并携带本人学生证或校园卡。", List.of("kb_exam_entry"), "exam_entry"),
                new EvalCase("补考没过是不是马上就能再补一次？", "不是。补考机会一般仅限一次；补考仍未通过的学生，应按学院教学安排申请课程重修。", List.of("kb_makeup_exam", "kb_retake_course"), "cross_policy"),
                new EvalCase("挂科后的补考和重修有什么区别？", "期末考试不及格可参加下一学期开学初组织的补考，补考机会一般仅限一次；补考仍未通过的学生应按学院教学安排申请课程重修。", List.of("kb_makeup_exam", "kb_retake_course"), "cross_policy"),
                new EvalCase("期末没过，下学期初那次机会叫什么？", "期末考试不及格的学生可参加下一学期开学初组织的补考。", List.of("kb_makeup_exam"), "makeup_exam"),
                new EvalCase("热门课满员了，但我进了候补名单，算选上了吗？", "不算。候补仅表示有空余名额时按规则递补，不代表学生已经完成正式选课；课程人数达到上限后系统不再接受新的选课请求。", List.of("kb_course_waitlist", "kb_course_capacity"), "cross_policy"),
                new EvalCase("课程显示容量已满，是不是还可以继续提交选课试试？", "不能。选课人数达到上限后，系统将不再接受新的选课请求。", List.of("kb_course_capacity"), "course_capacity"),
                new EvalCase("热门课候补成功前，我是不是已经完成正式选课？", "不是。候补不代表已经完成正式选课，只有出现空余名额并按规则递补后才可能完成选课。", List.of("kb_course_waitlist"), "course_waitlist"),
                new EvalCase("基础课还没修完，能不能先占个进阶课名额？", "不能。未修完指定基础课程的学生不能选修后续进阶课程。", List.of("kb_prerequisite"), "prerequisite"),
                new EvalCase("先修课没过但课程还有容量，可以选吗？", "不可以。课程有容量不等于满足先修要求，未修完指定基础课程的学生不能选修后续进阶课程。", List.of("kb_prerequisite", "kb_course_capacity"), "cross_policy"),
                new EvalCase("课程先修要求和课程容量限制哪个决定我能不能选？", "两者都可能影响选课：未修完指定基础课程不能选修后续进阶课程；课程人数达到上限后系统也不再接受新的选课请求。", List.of("kb_prerequisite", "kb_course_capacity"), "cross_policy"),
                new EvalCase("开学前两周没登录教务系统选课，之后还能新增吗？", "选课结束后原则上不再受理新增选课申请；确因培养方案调整需要新增课程的，应由学院统一审核后报教务处处理。", List.of("kb_course_selection_window", "kb_course_add_after_deadline"), "cross_policy"),
                new EvalCase("培养方案临时调整要新增课程，是不是学生自己直接加？", "不是。确因培养方案调整需要新增课程的，应由学院统一审核后报教务处处理。", List.of("kb_course_add_after_deadline"), "course_add_after_deadline"),
                new EvalCase("选课结束后原则上不受理新增，那补选阶段还存在吗？", "存在。选课结束后原则上不受理新增选课申请；但因系统故障或特殊原因未完成选课的学生，可在补选阶段提交申请。", List.of("kb_course_selection_window", "kb_course_makeup_select"), "cross_policy"),
                new EvalCase("总评是不是只看期末卷面成绩？", "不是。课程最终成绩由平时成绩、实验成绩和期末考试成绩综合评定，具体比例以课程大纲为准。", List.of("kb_course_score"), "course_score"),
                new EvalCase("实验课成绩和普通课程成绩构成完全一样吗？", "不一定。普通课程最终成绩由平时成绩、实验成绩和期末考试成绩综合评定；实验类课程还可能由实验预习、实验操作、实验报告和课程考核等部分综合评定，具体比例以课程大纲为准。", List.of("kb_course_score", "kb_lab_course_score"), "cross_policy"),
                new EvalCase("手机关机放包里带进考场，算不算相关资料？", "考试过程中不得携带与考试无关的电子设备或资料；违反考场纪律的学生将按学校考试违纪处理办法处理。", List.of("kb_exam_violation"), "exam_violation"),
                new EvalCase("进实验室只是旁听，不做实验，也要安全培训吗？", "知识库只说明进入实验室前应完成安全培训，并在实验过程中穿戴必要防护用品、遵守设备使用规范；旁听是否例外未说明，建议联系实验室管理人员确认。", List.of("kb_lab_safety"), "lab_safety"),
                new EvalCase("奖学金申请什么时候开始？", "抱歉，当前知识库中没有找到奖学金申请时间的相关信息。建议联系人工客服或学校相关部门获取准确信息。", List.of(), "fallback"),
                new EvalCase("校园卡丢失后怎么补办？", "抱歉，当前知识库中没有找到校园卡补办流程的相关信息。建议您联系人工客服或学校一卡通服务中心获取准确信息。", List.of(), "fallback")
        );

        if (cases.size() != 30) {
            throw new IllegalStateException("评测数据集数量必须为 30，实际为 " + cases.size());
        }
        return cases;
    }



    // ========== 真实向量库入库、检索和生成 ==========

    static MilvusClientV2 createMilvusClient() {
        return new MilvusClientV2(ConnectConfig.builder()
                .uri(MILVUS_URI)
                .build());
    }

    static void recreateCollection(MilvusClientV2 milvusClient) {
        Boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .build());
        if (Boolean.TRUE.equals(exists)) {
            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .build());
        }

        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_id")
                .dataType(DataType.VarChar)
                .isPrimaryKey(true)
                .maxLength(64)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("intent")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("title")
                .dataType(DataType.VarChar)
                .maxLength(512)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(4096)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("vector")
                .dataType(DataType.FloatVector)
                .dimension(EMBEDDING_DIMENSION)
                .build());

        milvusClient.createCollection(CreateCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .collectionSchema(schema)
                .build());
        milvusClient.createIndex(CreateIndexReq.builder()
                .collectionName(COLLECTION_NAME)
                .indexParams(List.of(IndexParam.builder()
                        .fieldName("vector")
                        .indexType(IndexParam.IndexType.AUTOINDEX)
                        .metricType(IndexParam.MetricType.COSINE)
                        .build()))
                .build());
        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .build());
    }

    static int importKnowledgeBase(MilvusClientV2 milvusClient, List<KnowledgeChunk> knowledgeBase) throws IOException {
        List<String> embeddingTexts = knowledgeBase.stream()
                .map(RAGEvaluator::toEmbeddingText)
                .toList();
        List<List<Float>> vectors = embed(embeddingTexts);

        List<JsonObject> rows = new ArrayList<>(knowledgeBase.size());
        for (int i = 0; i < knowledgeBase.size(); i++) {
            KnowledgeChunk chunk = knowledgeBase.get(i);
            JsonObject row = new JsonObject();
            row.addProperty("chunk_id", chunk.chunkId);
            row.addProperty("intent", chunk.intent);
            row.addProperty("title", chunk.title);
            row.addProperty("content", chunk.content);
            row.add("vector", gson.toJsonTree(vectors.get(i)));
            rows.add(row);
        }

        long count = milvusClient.insert(InsertReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(rows)
                .build()).getInsertCnt();
        return Math.toIntExact(count);
    }

    static List<RetrievedChunk> retrieve(MilvusClientV2 milvusClient, String query) throws IOException {
        List<Float> queryVector = embed(query);
        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(COLLECTION_NAME)
                .annsField("vector")
                .data(List.of(new FloatVec(queryVector)))
                .topK(TOP_K)
                .outputFields(List.of("chunk_id", "title", "content"))
                .searchParams(Map.of("metric_type", "COSINE"))
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build());

        if (searchResp.getSearchResults().isEmpty()) {
            return List.of();
        }

        List<RetrievedChunk> chunks = new ArrayList<>();
        for (SearchResp.SearchResult result : searchResp.getSearchResults().get(0)) {
            Map<String, Object> entity = result.getEntity();
            chunks.add(new RetrievedChunk(
                    Objects.toString(entity.get("chunk_id"), ""),
                    Objects.toString(entity.get("title"), ""),
                    "",
                    Objects.toString(entity.get("content"), ""),
                    result.getScore()
            ));
        }
        return chunks;
    }

    static List<Float> embed(String text) throws IOException {
        return embed(List.of(text)).get(0);
    }

    static List<List<Float>> embed(List<String> texts) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", EMBEDDING_MODEL);
        requestBody.add("input", gson.toJsonTree(texts));
        requestBody.addProperty("encoding_format", "float");

        Request request = new Request.Builder()
                .url(EMBEDDING_API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                throw new IOException("Embedding API 调用失败，状态码：" + response.code() + "，响应：" + body);
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray dataArray = json.getAsJsonArray("data");
            List<List<Float>> vectors = new ArrayList<>(dataArray.size());
            for (int i = 0; i < dataArray.size(); i++) {
                JsonArray embeddingArray = dataArray.get(i).getAsJsonObject().getAsJsonArray("embedding");
                List<Float> vector = new ArrayList<>(embeddingArray.size());
                for (int j = 0; j < embeddingArray.size(); j++) {
                    vector.add(embeddingArray.get(j).getAsFloat());
                }
                vectors.add(vector);
            }
            return vectors;
        }
    }

    static String generateAnswer(String query, List<RetrievedChunk> chunks) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", ANSWER_MODEL);
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", 512);

        JsonArray messages = new JsonArray();
        messages.add(message("system", "你是高校教务问答助手。请严格依据参考资料回答，不要编造参考资料中没有的信息。资料不足时请明确说明无法确认，并建议联系人工客服或教务处。"));
        messages.add(message("user", buildRagPrompt(query, chunks)));
        requestBody.add("messages", messages);

        Request request = new Request.Builder()
                .url(CHAT_API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = Objects.requireNonNull(response.body()).string();
            if (!response.isSuccessful()) {
                throw new IOException("回答模型调用失败，状态码：" + response.code() + "，响应：" + body);
            }

            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            return json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString()
                    .trim();
        }
    }

    static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    static String buildRagPrompt(String query, List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "【参考资料】\n（无可用参考资料）\n\n"
                    + "【用户问题】\n" + query + "\n\n"
                    + "请明确回答：根据现有资料，暂时无法确认，请联系人工客服或教务处。";
        }

        StringBuilder prompt = new StringBuilder("【参考资料】\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            prompt.append("[").append(i + 1).append("] ")
                    .append(chunk.content)
                    .append("\n");
        }
        prompt.append("\n【用户问题】\n").append(query).append("\n\n")
                .append("请用简洁中文回答，覆盖关键条件。");
        return prompt.toString();
    }

    static String toEmbeddingText(KnowledgeChunk chunk) {
        return chunk.title + "\n" + chunk.content;
    }

    static double topScore(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0.0;
        }
        return chunks.get(0).score;
    }

    static boolean containsFallbackAnswer(String answer) {
        if (answer == null) {
            return false;
        }
        return answer.contains("暂时无法") || answer.contains("无法确认")
                || answer.contains("没有找到") || answer.contains("联系人工")
                || answer.contains("联系教务处") || answer.contains("知识库中没有");
    }

    // ========== 检索指标计算 ==========

    /**
     * 计算命中率：Top-K 里有没有包含正确 chunk
     */
    static boolean calculateHit(List<String> retrievedIds, List<String> relevantIds) {
        if (relevantIds.isEmpty()) {
            return false;  // 兜底样本没有相关 chunk 标注，不参与命中判断
        }
        for (String id : retrievedIds) {
            if (relevantIds.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算倒数排名：正确 chunk 排在第几位
     */
    static double calculateReciprocalRank(List<String> retrievedIds, List<String> relevantIds) {
        if (relevantIds.isEmpty()) {
            return 0.0;  // 兜底样本没有相关 chunk 标注，不参与 MRR 计算
        }
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (relevantIds.contains(retrievedIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;  // Top-K 里没有正确答案
    }

    // ========== LLM 评分 ==========

    /**
     * 调用大模型进行评分
     */
    static ScoreResult llmScore(String scorePrompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", JUDGE_MODEL);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", scorePrompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);
        requestBody.addProperty("temperature", 0.1);
        requestBody.addProperty("max_tokens", 200);

        Request request = new Request.Builder()
                .url(CHAT_API_URL)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String content = json.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString()
                    .trim();

            // 提取 JSON 部分（模型可能输出额外文字）
            int start = content.indexOf("{");
            int end = content.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) {
                content = content.substring(start, end);
            }

            return gson.fromJson(content, ScoreResult.class);
        }
    }

    /**
     * 忠实度评分
     */
    static ScoreResult scoreFaithfulness(String chunks, String answer) throws IOException {
        String prompt = "你是一个专业的 RAG 系统评估员。你的任务是评估模型的回答是否忠实于给定的参考文档内容。\n\n"
                + "评分标准：\n"
                + "- 5 分：回答完全基于参考文档，没有添加任何文档中没有的信息\n"
                + "- 4 分：回答基本基于参考文档，有极少量合理推断但不影响准确性\n"
                + "- 3 分：回答部分基于参考文档，但添加了一些文档中没有的信息\n"
                + "- 2 分：回答包含较多文档中没有的信息，存在明显编造\n"
                + "- 1 分：回答与参考文档内容严重不符或大量编造\n\n"
                + "参考文档内容：\n" + chunks + "\n\n"
                + "模型的回答：\n" + answer + "\n\n"
                + "请按以下 JSON 格式输出评分结果，不要输出其他内容：\n"
                + "{\"score\": <1-5的整数>, \"label\": \"<faithful/partially_faithful/unfaithful>\", "
                + "\"reason\": \"<简要说明评分理由>\"}";
        return llmScore(prompt);
    }

    /**
     * 相关性评分
     */
    static ScoreResult scoreRelevancy(String query, String answer) throws IOException {
        String prompt = "你是一个专业的 RAG 系统评估员。你的任务是评估模型的回答是否回答了用户的问题。\n\n"
                + "评分标准：\n"
                + "- 5 分：直接、完整地回答了用户的问题\n"
                + "- 4 分：回答了用户的问题，但不够完整或包含了多余信息\n"
                + "- 3 分：部分回答了用户的问题，但遗漏了关键信息\n"
                + "- 2 分：回答与用户的问题有关，但没有真正回答问题\n"
                + "- 1 分：回答与用户的问题完全无关\n\n"
                + "用户问题：\n" + query + "\n\n"
                + "模型的回答：\n" + answer + "\n\n"
                + "请按以下 JSON 格式输出评分结果，不要输出其他内容：\n"
                + "{\"score\": <1-5的整数>, \"label\": \"<relevant/partially_relevant/irrelevant>\", "
                + "\"reason\": \"<简要说明评分理由>\"}";
        return llmScore(prompt);
    }

    /**
     * 正确率评分
     */
    static ScoreResult scoreCorrectness(String query, String expectedAnswer, String actualAnswer) throws IOException {
        String prompt = "你是一个专业的 RAG 系统评估员。你的任务是评估模型的回答是否正确。\n\n"
                + "评分标准：\n"
                + "- 5 分：回答与标准答案的含义完全一致\n"
                + "- 4 分：回答与标准答案基本一致，核心信息正确，细节略有差异\n"
                + "- 3 分：回答部分正确，但遗漏或错误了一些重要信息\n"
                + "- 2 分：回答包含正确信息，但主要结论有误\n"
                + "- 1 分：回答与标准答案完全不一致\n\n"
                + "用户问题：\n" + query + "\n\n"
                + "标准答案：\n" + expectedAnswer + "\n\n"
                + "模型的回答：\n" + actualAnswer + "\n\n"
                + "请按以下 JSON 格式输出评分结果，不要输出其他内容：\n"
                + "{\"score\": <1-5的整数>, \"label\": \"<correct/partially_correct/incorrect>\", "
                + "\"reason\": \"<简要说明评分理由>\"}";
        return llmScore(prompt);
    }



    // ========== 评估报告 ==========

    static void printEvalReport(List<EvalResult> results) {
        System.out.println("=" .repeat(70));
        System.out.println("                    RAG 系统评估报告");
        System.out.println("=" .repeat(70));

        // --- 检索指标 ---
        List<EvalResult> retrievalResults = results.stream()
                .filter(r -> !r.evalCase.relevantChunkIds.isEmpty())
                .toList();
        long hitCount = retrievalResults.stream().filter(r -> r.hit).count();
        double hitRate = (double) hitCount / retrievalResults.size();
        double mrr = retrievalResults.stream()
                .mapToDouble(r -> r.reciprocalRank).average().orElse(0);

        System.out.println("\n【检索阶段指标】");
        System.out.printf("  命中率（Hit Rate）：%.1f%%（%d / %d）%n",
                hitRate * 100, hitCount, retrievalResults.size());
        System.out.printf("  MRR（平均倒数排名）：%.3f%n", mrr);

        // --- 生成指标 ---
        double avgFaithfulness = results.stream()
                .filter(r -> r.faithfulness != null)
                .mapToInt(r -> r.faithfulness.score).average().orElse(0);
        double avgRelevancy = results.stream()
                .filter(r -> r.relevancy != null)
                .mapToInt(r -> r.relevancy.score).average().orElse(0);
        long hallucinationCount = results.stream()
                .filter(r -> r.faithfulness != null && r.faithfulness.score <= 2)
                .count();
        double hallucinationRate = (double) hallucinationCount / results.size();

        System.out.println("\n【生成阶段指标】");
        System.out.printf("  忠实度平均分：%.2f / 5.0%n", avgFaithfulness);
        System.out.printf("  相关性平均分：%.2f / 5.0%n", avgRelevancy);
        System.out.printf("  明显幻觉率：%.1f%%（%d / %d 条存在明显幻觉）%n",
                hallucinationRate * 100, hallucinationCount, results.size());

        // --- 端到端指标 ---
        double avgCorrectness = results.stream()
                .filter(r -> r.correctness != null)
                .mapToInt(r -> r.correctness.score).average().orElse(0);
        long exactCorrectCount = results.stream()
                .filter(r -> r.correctness != null && r.correctness.score == 5)
                .count();
        double exactCorrectRate = (double) exactCorrectCount / results.size();
        boolean stablePass = avgCorrectness >= STABLE_AVERAGE_SCORE && exactCorrectRate >= 0.8;

        // 兜底率：回答中包含抱歉、找不到、没有找到等关键词的比例
        long fallbackCount = results.stream()
                .filter(r -> containsFallbackAnswer(r.actualAnswer))
                .count();
        double fallbackRate = (double) fallbackCount / results.size();

        List<EvalResult> noAnswerResults = results.stream()
                .filter(r -> r.evalCase.relevantChunkIds.isEmpty())
                .toList();
        long noAnswerCorrectCount = noAnswerResults.stream()
                .filter(r -> r.fallbackAnswer)
                .count();
        double noAnswerCorrectRate = noAnswerResults.isEmpty() ? 0.0 : (double) noAnswerCorrectCount / noAnswerResults.size();

        System.out.println("\n【端到端指标】");
        System.out.printf("  正确率评分均值：%.2f / 5.0%n", avgCorrectness);
        System.out.printf("  严格答案正确率（=5 分）：%.1f%%（%d / %d）%n",
                exactCorrectRate * 100, exactCorrectCount, results.size());
        System.out.printf("  稳定通过：%s（均分需 ≥ %.1f，且 =5 分比例需 ≥ 80%%）%n",
                stablePass ? "是" : "否", STABLE_AVERAGE_SCORE);
        System.out.printf("  兜底率：%.1f%%（%d / %d）%n",
                fallbackRate * 100, fallbackCount, results.size());
        System.out.printf("  无答案识别准确率：%.1f%%（%d / %d）%n",
                noAnswerCorrectRate * 100, noAnswerCorrectCount, noAnswerResults.size());

        // --- Bad Case 列表 ---
        System.out.println("\n【Bad Case 列表】（正确率评分 < 5 分或无答案未兜底的问题）");
        System.out.println("-".repeat(70));
        boolean hasBadCase = false;
        for (EvalResult r : results) {
            boolean noAnswerFailed = r.evalCase.relevantChunkIds.isEmpty() && !r.fallbackAnswer;
            if ((r.correctness != null && r.correctness.score < 5) || noAnswerFailed) {
                hasBadCase = true;
                System.out.printf("  问题：%s%n", r.evalCase.query);
                System.out.printf("  期望答案：%s%n", r.evalCase.expectedAnswer);
                System.out.printf("  实际答案：%s%n", r.actualAnswer);
                System.out.printf("  TopScore：%.4f | 检索命中：%s | 无答案兜底：%s | 忠实度：%d 分 | 相关性：%d 分 | 正确率：%d 分%n",
                        r.topScore,
                        r.hit ? "是" : "否",
                        r.fallbackAnswer ? "是" : "否",
                        r.faithfulness != null ? r.faithfulness.score : 0,
                        r.relevancy != null ? r.relevancy.score : 0,
                        r.correctness.score);
                // 问题归因
                if (!r.hit) {
                    System.out.println("  → 问题归因：【检索阶段】未命中正确 chunk");
                } else if (r.faithfulness != null && r.faithfulness.score <= 3) {
                    System.out.println("  → 问题归因：【生成阶段】回答与 chunk 内容不够一致，存在编造或额外推断");
                } else {
                    System.out.println("  → 问题归因：【知识库】chunk 内容可能不完整或过时");
                }
                System.out.println("-".repeat(70));
            }
        }
        if (!hasBadCase) {
            System.out.println("  无 Bad Case，所有评测问题的正确率评分均为 5 分，且无答案样本均正确兜底");
        }

        System.out.println("\n" + "=".repeat(70));
    }


    public static void main(String[] args) throws Exception {
        // 1. 构建知识库和评测数据集
        List<KnowledgeChunk> knowledgeBase = buildKnowledgeBase();
        List<EvalCase> evalDataset = buildEvalDataset();
        System.out.println("知识库条款：" + knowledgeBase.size() + " 条");
        System.out.println("评测数据集：" + evalDataset.size() + " 条");

        // 2. 真实入库：重建专用集合，只把知识库条款写入向量数据库
        MilvusClientV2 milvusClient = createMilvusClient();
        recreateCollection(milvusClient);
        int inserted = importKnowledgeBase(milvusClient, knowledgeBase);
        System.out.println("已写入 Milvus Collection：" + COLLECTION_NAME + "，数量：" + inserted + " 条");

        // 3. 逐条评测：真实向量检索 -> RAG 生成 -> LLM Judge 评分
        List<EvalResult> evalResults = new ArrayList<>();
        for (int i = 0; i < evalDataset.size(); i++) {
            EvalCase evalCase = evalDataset.get(i);
            System.out.printf("\n评测第 %d/%d 条：%s%n", i + 1, evalDataset.size(), evalCase.query);

            EvalResult result = new EvalResult();
            result.evalCase = evalCase;

            // 真实检索和生成
            result.retrievedChunks = retrieve(milvusClient, evalCase.query);
            result.retrievedChunkIds = result.retrievedChunks.stream()
                    .map(chunk -> chunk.chunkId)
                    .toList();
            result.topScore = topScore(result.retrievedChunks);
            List<RetrievedChunk> chunksForAnswer = result.topScore < NO_ANSWER_SCORE_THRESHOLD
                    ? List.of()
                    : result.retrievedChunks;
            result.actualAnswer = generateAnswer(evalCase.query, chunksForAnswer);
            result.fallbackAnswer = containsFallbackAnswer(result.actualAnswer);

            // 计算检索指标
            result.hit = calculateHit(result.retrievedChunkIds, evalCase.relevantChunkIds);
            result.reciprocalRank = calculateReciprocalRank(result.retrievedChunkIds, evalCase.relevantChunkIds);
            if (evalCase.relevantChunkIds.isEmpty()) {
                System.out.printf("  检索评估：无答案样本，TopScore：%.4f，阈值：%.2f%n",
                        result.topScore, NO_ANSWER_SCORE_THRESHOLD);
            } else {
                System.out.printf("  检索命中：%s，倒数排名：%.2f，TopScore：%.4f%n",
                        result.hit ? "是" : "否", result.reciprocalRank, result.topScore);
            }

            // 组装检索到的 chunk 内容
            StringBuilder chunkText = new StringBuilder();
            for (RetrievedChunk chunk : chunksForAnswer) {
                chunkText.append("[").append(chunk.chunkId).append("] ")
                        .append(chunk.content)
                        .append(" score=")
                        .append(String.format(Locale.ROOT, "%.4f", chunk.score))
                        .append("\n");
            }
            String chunks = !chunkText.isEmpty() ? chunkText.toString() : "（未检索到相关内容）";

            // LLM 评分（三个维度）
            System.out.println("  正在评分...");
            result.faithfulness = scoreFaithfulness(chunks, result.actualAnswer);
            System.out.printf("  忠实度：%d 分 - %s%n", result.faithfulness.score, result.faithfulness.reason);

            result.relevancy = scoreRelevancy(evalCase.query, result.actualAnswer);
            System.out.printf("  相关性：%d 分 - %s%n", result.relevancy.score, result.relevancy.reason);

            result.correctness = scoreCorrectness(evalCase.query, evalCase.expectedAnswer, result.actualAnswer);
            System.out.printf("  正确率：%d 分 - %s%n", result.correctness.score, result.correctness.reason);

            evalResults.add(result);
        }

        // 4. 输出评估报告
        System.out.println();
        printEvalReport(evalResults);
    }
}
