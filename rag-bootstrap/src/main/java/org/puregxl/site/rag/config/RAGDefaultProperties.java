package org.puregxl.site.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "rag.default")
public class RAGDefaultProperties {
    /**
     * 默认向量集合名称
     * <p>
     * 用于指定在向量数据库中存储向量数据的默认集合（Collection）名称
     */
    private String collectionName = "test2";

    /**
     * 向量维度
     * <p>
     * 指定向量的维数，需要与所使用的 Embedding 模型输出维度保持一致
     * 例如：2048、4096 等
     */
    private Integer dimension = 4096;


    /**
     * SSE 全局超时时间（毫秒）
     * <p>
     * 兜底防止 SSE 连接泄漏，超时后自动关闭连接。默认 5 分钟
     */
    private Long sseTimeoutMs = 5 * 60 * 1000L;

    /**
     * 向量召回数量。
     * <p>
     * 先多召回一些候选 Chunk，再交给 rerank 做精排，避免第一阶段召回过窄导致答案上下文不足。
     */
    private Integer retrieveTopK = 8;

    /**
     * 精排后喂给大模型的 Chunk 数量。
     */
    private Integer rerankTopN = 4;
}
