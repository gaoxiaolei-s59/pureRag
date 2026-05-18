package org.puregxl.site.bootstrap.knowledge.dto.request;

import lombok.Data;

@Data
public class KnowledgeDocumentUpdateRequest {

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否开启定时拉取
     */
    private Boolean scheduleEnabled;

    /**
     * 定时表达式（cron）
     */
    private String scheduleCron;

    /**
     * 分块策略
     */
    private String chunkStrategy;

    /**
     * 分块参数配置（JSON）
     */
    private String chunkConfig;
}
