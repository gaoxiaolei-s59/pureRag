package org.puregxl.site.knowledge.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class KnowledgeBaseInfoResponse {
    /**
     * 知识库ID
     */
    private String id;

    /**
     * 知识库名称
     */
    private String name;
}
