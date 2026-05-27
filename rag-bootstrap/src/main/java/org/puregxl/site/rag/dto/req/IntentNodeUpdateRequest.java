package org.puregxl.site.rag.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntentNodeUpdateRequest {
    /**
     * 关联知识库 ID。
     * KB 类型节点更新时，服务端会优先基于该字段反查 collectionName。
     */
    private String kbId;

    @NotBlank(message = "意图编码不能为空")
    private String intentCode;

    @NotBlank(message = "意图名称不能为空")
    private String name;

    @NotNull(message = "意图层级不能为空")
    private Integer level;

    private String parentCode;
    private String description;
    private List<String> examples;

    private String collectionName;
    private String mcpToolId;
    private Integer topK;

    @NotNull(message = "意图类型不能为空")
    private Integer kind;

    private Integer sortOrder;

    @NotNull(message = "启用状态不能为空")
    private Integer enabled;
    private String promptSnippet;
    private String promptTemplate;
    private String paramPromptTemplate;
}
