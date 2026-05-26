package org.puregxl.site.rag.dto.resp;

import lombok.Data;
import org.puregxl.site.rag.enums.IntentLevel;

@Data
public class IntentNodeResponse {
    /**
     * 唯一标识，如：
     * - "group" / "group-hr" / "biz-oa-intro" / "middleware-redis"
     */
    private String id;

    /**
     * 知识库 ID
     */
    private String kbId;

    /**
     * 展示名称，如「人事」「OA系统」「数据安全」
     */
    private String name;

    /**
     * 语义说明，用于向量化时的语义提示词
     */
    private String description;

    /**
     * 所属层级：DOMAIN / CATEGORY / TOPIC
     */
    private IntentLevel level;

    /**
     * 父节点 ID，根节点为 null
     */
    private String parentId;
}
