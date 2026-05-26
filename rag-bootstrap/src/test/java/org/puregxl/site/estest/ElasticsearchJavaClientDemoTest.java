package org.puregxl.site.estest;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Elasticsearch Java Client 最小使用示例。
 * <p>
 * 这个测试不依赖 Spring 容器，主要演示：连接 ES -> 创建索引 -> 写入文档 -> 查询文档 -> 清理测试数据。
 * 默认连接本地 {@code http://localhost:9200}，也可以通过 JVM 参数 {@code -Des.url=http://host:9200} 指定地址。
 */
class ElasticsearchJavaClientDemoTest {

    private static final String ES_URL = System.getProperty("es.url", "http://localhost:9200");
    private static final String INDEX_NAME = "pureagent_demo_docs";
    private static final String DEMO_DOC_ID = "demo-001";

    private RestClient restClient;
    private ElasticsearchTransport transport;
    private ElasticsearchClient client;

    /**
     * 演示 ES Java Client 的文档增删改查。
     * <p>
     * 主流程覆盖：创建索引 -> 插入文档 -> 查询文档 -> 修改文档 -> 再次查询 -> 删除文档。
     * 如果本地 ES 没有启动，测试会自动跳过，避免影响项目常规构建；启动 ES 后可直接运行该测试观察控制台输出。
     */
    @Test
    void documentCrudDemo() throws IOException {
        initClient();
        Assumptions.assumeTrue(elasticsearchAvailable(), "本地 Elasticsearch 未启动，跳过 ES demo 测试");

        recreateIndex();

        // 1. 插入：写入一条模拟知识库文档 Chunk，refresh=true 方便后续立即查询到。
        Map<String, Object> document = Map.of(
                "docId", DEMO_DOC_ID,
                "title", "PureAgent Elasticsearch Demo",
                "content", "Elasticsearch 可以用于关键词检索，也可以和向量检索组合做混合召回。",
                "chunkCount", 3
        );
        client.index(request -> request
                .index(INDEX_NAME)
                .id(DEMO_DOC_ID)
                .document(document)
                .refresh(Refresh.True));

        GetResponse<Map> insertedDocument = client.get(request -> request
                        .index(INDEX_NAME)
                        .id(DEMO_DOC_ID),
                Map.class);
        assertThat(insertedDocument.found()).isTrue();
        assertThat(insertedDocument.source()).containsEntry("docId", DEMO_DOC_ID);

        // 2. 查询：使用 IK 中文分词字段检索内容，验证可以召回刚写入的文档。
        SearchResponse<Map> response = client.search(request -> request
                        .index(INDEX_NAME)
                        .query(query -> query
                                .multiMatch(match -> match
                                        .query("Elasticsearch 混合召回")
                                        .fields("title", "content")))
                        .size(5),
                Map.class);

        List<Hit<Map>> hits = response.hits().hits();
        hits.forEach(hit -> System.out.printf("score=%s, id=%s, source=%s%n", hit.score(), hit.id(), hit.source()));
        assertThat(hits).extracting(Hit::id).contains(DEMO_DOC_ID);

        // 3. 修改：更新部分字段，模拟文档重新切块后 Chunk 数量和内容发生变化。
        client.update(request -> request
                        .index(INDEX_NAME)
                        .id(DEMO_DOC_ID)
                        .doc(Map.of(
                                "content", "PureAgent 使用 Elasticsearch 做中文关键词检索，并结合向量数据库实现混合检索。",
                                "chunkCount", 5))
                        .refresh(Refresh.True),
                Map.class);

        GetResponse<Map> updatedDocument = client.get(request -> request
                        .index(INDEX_NAME)
                        .id(DEMO_DOC_ID),
                Map.class);
        assertThat(updatedDocument.found()).isTrue();
        Map updatedSource = updatedDocument.source();
        assertThat(updatedSource).containsEntry("chunkCount", 5);
        assertThat(String.valueOf(updatedSource.get("content"))).contains("中文关键词检索");

        // 4. 删除：删除文档后再次 exists 检查，确认文档已经不存在。
        client.delete(request -> request
                .index(INDEX_NAME)
                .id(DEMO_DOC_ID)
                .refresh(Refresh.True));
        assertThat(client.exists(request -> request
                .index(INDEX_NAME)
                .id(DEMO_DOC_ID)).value()).isFalse();
    }



    @Test
    void documentCrudDemo1() throws IOException {
        initClient();
        Assumptions.assumeTrue(elasticsearchAvailable(), "本地 Elasticsearch 未启动，跳过 ES demo 测试");

        recreateIndex();

        // 插入测试数据
        insertDemoDocuments();

//        Map<String, Object> document = Map.of(
//                "docId", "doc-009",
//                "title", "Docker Compose 入门",
//                "content", "Docker Compose 可以通过一个 YAML 文件管理多个容器服务。",
//                "chunkCount", 4
//        );


        Document document = Document.builder()
                .docId("doc-009").title("Docker Compose 入门").content("Docker Compose 可以通过一个 YAML 文件管理多个容器服务。").chunkCount(4).build();


        client.index(request -> request
                .index(INDEX_NAME)
                .id("doc-009")
                .document(document)
                .refresh(Refresh.True));

        GetResponse<Map> response = client.get(request -> request
                        .index(INDEX_NAME)
                        .id("doc-001"),
                Map.class);

        System.out.println(response.source());

        SearchResponse<Document> response1 = client.search(request -> request
                        .index(INDEX_NAME)
                        .query(query -> query
                                .match(match -> match
                                        .field("content")
                                        .query("向量数据库")))
                        .size(5),
                Document.class);


        SearchResponse<Document> response2 = client.search(request -> request
                        .index(INDEX_NAME)
                        .query(query -> query
                                .multiMatch(multiMatch -> multiMatch
                                        .query("向量数据库")
                                        .fields("content", "title")))
                        .size(5),
                Document.class);


        List<Hit<Document>> hits = response2.hits().hits();

        for (Hit<Document> hit : hits) {
            System.out.println(hit.source());
        }


    }


    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Document {
        private String docId;

        private String title;

        private String content;

        private Integer chunkCount;
    }

    /**
     * 批量插入测试数据。
     */
    private void insertDemoDocuments() throws IOException {
        List<Map<String, Object>> documents = List.of(
                Map.of(
                        "docId", "doc-001",
                        "title", "Elasticsearch 入门教程",
                        "content", "Elasticsearch 是一个分布式搜索引擎，适合做全文检索和日志分析。",
                        "chunkCount", 3
                ),
                Map.of(
                        "docId", "doc-002",
                        "title", "Spring AI RAG 实战",
                        "content", "Spring AI 可以结合向量数据库和 Elasticsearch 实现 RAG 知识库检索。",
                        "chunkCount", 8
                ),
                Map.of(
                        "docId", "doc-003",
                        "title", "MySQL 索引优化",
                        "content", "MySQL 查询优化通常需要关注索引设计、执行计划和慢 SQL 分析。",
                        "chunkCount", 5
                ),
                Map.of(
                        "docId", "doc-004",
                        "title", "Redis 缓存设计",
                        "content", "Redis 常用于缓存、分布式锁、排行榜和限流场景。",
                        "chunkCount", 4
                ),
                Map.of(
                        "docId", "doc-005",
                        "title", "Java 并发编程",
                        "content", "Java 并发编程涉及线程池、CompletableFuture、锁机制和并发集合。",
                        "chunkCount", 6
                ),
                Map.of(
                        "docId", "doc-006",
                        "title", "RocketMQ 消息队列",
                        "content", "RocketMQ 支持普通消息、延迟消息、事务消息和顺序消息。",
                        "chunkCount", 7
                ),
                Map.of(
                        "docId", "doc-007",
                        "title", "Elasticsearch 混合召回",
                        "content", "混合召回通常结合 BM25 关键词检索、向量检索和 rerank 重排序。",
                        "chunkCount", 9
                ),
                Map.of(
                        "docId", "doc-008",
                        "title", "知识库问答系统设计",
                        "content", "知识库问答系统通常包含文档解析、切片、向量化、检索、重排和 Prompt 组装。",
                        "chunkCount", 10
                )
        );

        for (Map<String, Object> document : documents) {
            String docId = String.valueOf(document.get("docId"));

            client.index(request -> request
                    .index(INDEX_NAME)
                    .id(docId)
                    .document(document)
                    .refresh(Refresh.True));
        }
    }

    /**
     * 初始化 ES 客户端。
     * <p>
     * Java API Client 通过低层 RestClient 发送 HTTP 请求，RestClientTransport 负责把 JSON 请求/响应映射成强类型对象。
     */
    private void initClient() {
        URI uri = URI.create(ES_URL);
        restClient = RestClient.builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme())).build();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        client = new ElasticsearchClient(transport);
    }

    /**
     * 重建 demo 索引，保证每次运行都是干净数据。
     * <p>
     * 真实业务里通常不要直接删除索引，这里只用于测试演示。
     */
    private void recreateIndex() throws IOException {
        boolean exists = client.indices().exists(request -> request.index(INDEX_NAME)).value();
        if (exists) {
            client.indices().delete(request -> request.index(INDEX_NAME));
        }

        client.indices().create(request -> request
                .index(INDEX_NAME)
                .mappings(buildDemoMapping()));
    }

    /**
     * 检查 ES 是否可用。
     * <p>
     * 这里把连接失败转换成 JUnit skip 条件，避免本地没有启动 ES 时影响项目整体测试。
     */
    private boolean elasticsearchAvailable() {
        try {
            return client.ping().value();
        } catch (IOException ex) {
            return false;
        }
    }

    /**
     * 构造 demo 索引 mapping。
     * <p>
     * docId 用 keyword 做精确过滤；title/content 用 IK 中文分词做全文检索；chunkCount 用 integer 存储统计值。
     */
    private TypeMapping buildDemoMapping() {
        return TypeMapping.of(mapping -> mapping
                .properties("docId", property -> property.keyword(keyword -> keyword))
                .properties("title", property -> property.text(text -> text
                        .analyzer("ik_max_word")
                        .searchAnalyzer("ik_smart")))
                .properties("content", property -> property.text(text -> text
                        .analyzer("ik_max_word")
                        .searchAnalyzer("ik_smart")))
                .properties("chunkCount", property -> property.integer(integer -> integer)));
    }

    @AfterEach
    void closeClient() throws IOException {
        if (client != null) {
            try {
                client.indices().delete(request -> request.index(INDEX_NAME).ignoreUnavailable(true));
            } catch (IOException ignored) {
                // demo 环境下 ES 可能未启动，关闭资源即可。
            }
        }
        if (transport != null) {
            transport.close();
        }
        if (restClient != null) {
            restClient.close();
        }
    }
}
