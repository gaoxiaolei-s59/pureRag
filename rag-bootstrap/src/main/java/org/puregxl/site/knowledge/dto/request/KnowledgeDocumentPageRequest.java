package org.puregxl.site.knowledge.dto.request;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

@Data
public class KnowledgeDocumentPageRequest extends Page {

    /**
     * 文档名称关键字
     */
    private String keyword;

    /**
     * 文档状态：pending / running / failed / success
     */
    private String status;
}
