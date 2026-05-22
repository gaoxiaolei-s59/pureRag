package org.puregxl.site.knowledge.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 知识库 Chunk 视图对象
 */
@Data
public class KnowledgeChunkResponse {

    /**
     * ID
     */
    private String id;

    /**
     * 知识库ID
     */
    private String kbId;

    /**
     * 文档ID
     */
    private String docId;

    /**
     * 分块序号（从0开始）
     */
    private Integer chunkIndex;

    /**
     * 分块正文内容
     */
    private String content;

    /**
     * 内容哈希（用于幂等/去重）
     */
    private String contentHash;

    /**
     * 字符数
     */
    private Integer charCount;

    /**
     * Token数
     */
    private Integer tokenCount;

    /**
     * 是否启用 0：禁用 1：启用
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
