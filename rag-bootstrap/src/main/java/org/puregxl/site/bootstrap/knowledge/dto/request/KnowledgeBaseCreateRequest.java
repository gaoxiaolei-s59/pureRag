package org.puregxl.site.bootstrap.knowledge.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeBaseCreateRequest {
    /**
     * 知识库名称
     */
    @NotBlank(message = "知识库名称不能为空")
    private String name;

    /**
     * 嵌入模型，如 qwen3-embedding:8b-fp16
     */
    @NotBlank(message = "嵌入模型不能为空")
    private String embeddingModel;

    /**
     * Milvus Collection 名称
     */
    @NotBlank(message = "Milvus Collection 名称不能为空")
    private String collectionName;
}
