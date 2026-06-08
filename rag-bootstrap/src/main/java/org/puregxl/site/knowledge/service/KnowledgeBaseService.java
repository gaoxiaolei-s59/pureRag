package org.puregxl.site.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseCreateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBasePageRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseUpdateRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeBaseInfoResponse;
import org.puregxl.site.knowledge.dto.response.KnowledgeBaseResponse;

import java.util.List;

public interface KnowledgeBaseService {

    void createKnowledgeBase(KnowledgeBaseCreateRequest request);

    void renameKnowledgeBase(String kbId, KnowledgeBaseUpdateRequest request);

    void delete(String kbId);

    KnowledgeBaseResponse queryKnowledgeBaseById(String kbId);

    IPage<KnowledgeBaseResponse> pageQuery(KnowledgeBasePageRequest requestParam);

    List<String> queryModels();

    List<KnowledgeBaseInfoResponse> queryAllKnowledgeBase();
}
