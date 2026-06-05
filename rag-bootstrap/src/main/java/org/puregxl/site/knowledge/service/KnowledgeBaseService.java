package org.puregxl.site.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseCreateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBasePageRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseUpdateRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeBaseResponse;

import java.util.List;

public interface KnowledgeBaseService {

    /**
     * 创建知识库对应的 Milvus Collection 和 RustFS 存储目录。
     *
     * @param request 创建请求
     */
    void createKnowledgeBase(KnowledgeBaseCreateRequest request);

    void renameKnowledgeBase(String kbId, KnowledgeBaseUpdateRequest request);

    void delete(String kbId);

    KnowledgeBaseResponse queryKnowledgeBaseById(String kbId);

    IPage<KnowledgeBaseResponse> pageQuery(KnowledgeBasePageRequest requestParam);

    List<String> queryModels();
}
