package org.puregxl.site.knowledge.dto.request;

import lombok.Data;

@Data
public class KnowledgeBaseUpdateRequest {
    /**
     * 知识库名称（可修改）
     */
    private String name;
}
