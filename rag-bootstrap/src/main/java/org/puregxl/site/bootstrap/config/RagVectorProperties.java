package org.puregxl.site.bootstrap.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * RAG 向量数据库配置。
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "rag.vector")
public class RagVectorProperties {

    /**
     * 向量库类型，当前默认使用 Milvus。
     */
    @NotBlank
    private String type = "milvus";

    /**
     * Milvus 连接与集合配置。
     */
    @Valid
    private MilvusConfig milvus = new MilvusConfig();

    @Data
    public static class MilvusConfig {

        /**
         * Milvus 服务地址。
         */
        @NotBlank
        private String uri = "http://localhost:19530";

        /**
         * Collection 名称。
         */
        @NotBlank
        private String collectionName = "user_resume_vector";

        /**
         * 向量维度，需要和 embedding 模型输出保持一致。
         */
        @Min(1)
        private Integer dimension = 4096;

        /**
         * 相似度度量方式。
         */
        @NotBlank
        private String metricType = "COSINE";

        /**
         * 是否自动初始化 collection schema。
         */
        private Boolean initializeSchema = true;
    }
}
