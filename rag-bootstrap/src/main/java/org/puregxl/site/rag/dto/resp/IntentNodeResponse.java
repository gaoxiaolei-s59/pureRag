package org.puregxl.site.rag.dto.resp;

import lombok.Data;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.enums.IntentLevel;

import java.util.List;

@Data
public class IntentNodeResponse {
    /**
     * 数据库主键，供前端执行更新/删除时传给 CRUD 接口。
     */
    private String recordId;

    /**
     * 业务唯一标识，如 group-hr / biz-oa-intro。
     */
    private String id;

    /**
     * 关联知识库 ID。
     */
    private String kbId;

    /**
     * 节点展示名称。
     */
    private String name;

    /**
     * 节点描述。
     */
    private String description;

    /**
     * 示例问题列表。
     */
    private List<String> examples;

    /**
     * 所属层级。
     */
    private IntentLevel level;

    /**
     * 父节点业务 ID，根节点为 null。
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
     * 节点类型。
     */
    private IntentKind kind;

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
     * 当前节点直接子节点业务 ID 列表。
     */
    private List<String> children;
}
