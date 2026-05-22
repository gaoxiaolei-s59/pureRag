package org.puregxl.site.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.response.InsertResp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MilvusInsertDemo {
    private static final String API_KEY = "sk-rjtfqcpnhpzonswkebygmaqnqvibqcndgqxqfxghizuguthf";
    private static EmbeddingClient embeddingClient = new EmbeddingClient(API_KEY);
    private static final Gson GSON = new Gson();
    public static void main(String[] args) throws IOException, InterruptedException {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri("http://localhost:19530")
                .build();

        MilvusClientV2 client = new MilvusClientV2(connectConfig);

        List<String> chunkTexts = List.of(
                "选课规则：学生应在每学期开学前两周内登录教务系统完成选课。选课结束后，原则上不再受理新增选课申请。",
                "退课规则：学生在开课后第一周内可申请退课，超过规定时间退课需经任课教师和学院审批。",
                "补选安排：因系统故障或特殊原因未完成选课的学生，可在补选阶段提交申请，补选时间一般为开学第二周。",
                "课程容量说明：热门课程采用容量限制机制，选课人数达到上限后系统将不再接受新的选课请求。",
                "先修课程要求：部分专业课程设置先修要求，未修完指定基础课程的学生不能选修后续进阶课程。",
                "考试安排：期末考试时间以教务处统一发布的考试通知为准，学生应至少提前 15 分钟进入考场。",
                "考试证件要求：参加考试时，学生须携带本人学生证或校园卡，证件不齐者不得进入考场。",
                "缓考申请：因生病或重大突发情况无法参加考试的学生，应在考试前提交缓考申请，并附相关证明材料。",
                "补考规定：期末考试不及格的学生可参加下一学期开学初组织的补考，补考机会一般仅限一次。"
                );

        List<double[]> vectors = embeddingClient.embed(chunkTexts);

        // 组装插入数据
        List<JsonObject> rows = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("chunk_text", chunkTexts.get(i));
            row.addProperty("doc_id", "doc_return_" + i);
            row.addProperty("category", "return_policy");
            row.add("vector", GSON.toJsonTree(vectors.get(i)));
            rows.add(row);
        }

        // 插入 Milvus
        InsertReq insertReq = InsertReq.builder()
                .collectionName("customer_service_chunks")
                .data(rows)
                .build();
        InsertResp insertResp = client.insert(insertReq);
        System.out.println("插入成功，数量：" + insertResp.getInsertCnt());

    }


}
