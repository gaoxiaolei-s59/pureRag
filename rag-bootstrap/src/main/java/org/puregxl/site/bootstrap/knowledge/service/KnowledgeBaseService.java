package org.puregxl.site.bootstrap.knowledge.service;

import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeBaseCreateRequest;

public interface KnowledgeBaseService {

    /**
     * 创建知识库对应的 Milvus Collection 和 RustFS 存储目录。
     *
     * @param request 创建请求
     */
    void createKnowledgeBase(KnowledgeBaseCreateRequest request);
}
