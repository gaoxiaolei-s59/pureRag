package org.puregxl.site.rag.dto.resp;

import lombok.Data;
import org.puregxl.site.rag.enums.IntentLevel;

@Data
public class IntentNodeResponse {
    /**
     * 数据库主键，供前端执行更新/删除时传给 CRUD 接口。
     */
    private String recordId;

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
     * 示例问题
     */
    private java.util.List<String> examples;

    /**
     * 所属层级：DOMAIN / CATEGORY / TOPIC
     */
    private IntentLevel level;

    /**
     * 父节点 ID，根节点为 null
     */
    private String parentId;

    /**
     * Collection 名称，仅对 KB 类型节点有意义。
     */
    private String collectionName;

    /**
     * MCP 工具 ID，仅对 MCP 类型节点有意义。
     */
    private String mcpToolId;

    /**
     * 节点类型：KB / MCP / SYSTEM。
     */
    private org.puregxl.site.rag.enums.IntentKind kind;

    private Integer topK;

    private Integer sortOrder;

    private Integer enabled;

    private String promptSnippet;

    private String promptTemplate;

    private String paramPromptTemplate;

    /**
     * 仅用于排查/展示的完整路径。
     */
    private String fullPath;

    /**
     * 子节点业务 ID 列表。
     */
    private java.util.List<String> children;
}
