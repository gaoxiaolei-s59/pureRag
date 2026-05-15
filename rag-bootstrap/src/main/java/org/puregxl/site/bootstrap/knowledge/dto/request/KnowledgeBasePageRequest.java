package org.puregxl.site.bootstrap.knowledge.dto.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Builder;
import lombok.Data;

@Data
public class KnowledgeBasePageRequest extends Page {

    /**
     * 知识库名称（支持模糊匹配）
     */
    private String name;
}
