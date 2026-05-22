package org.puregxl.site.knowledge.dto.request;

import lombok.Data;

/**
 * 知识库更新请求
 */
@Data
public class KnowledgeChunkUpdateRequest {
    /**
     * 分块正文内容
     */
    private String content;
}
