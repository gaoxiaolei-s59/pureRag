package org.puregxl.site.bootstrap.knowledge.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 批量禁用请求
 */
@Data
public class KnowledgeChunkBatchRequest {
    /**
     * Chunk ID 列表（可选，不传则操作文档下所有 chunk）
     */
    private List<String> chunkIds;
}
