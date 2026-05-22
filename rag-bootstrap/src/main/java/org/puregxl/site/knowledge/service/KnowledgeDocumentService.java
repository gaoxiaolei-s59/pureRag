package org.puregxl.site.knowledge.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentPageRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentUpdateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeDocumentResponse;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeDocumentService {
    KnowledgeDocumentResponse uploadKnowledgeDocument(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file);

    void startChunkKnowledgeDocument(String docId);

    void deleteKnowledgeDocument(String docId);

    KnowledgeDocumentResponse getKnowledgeDocument(String docId);

    void updateKnowledgeDocument(String docId, KnowledgeDocumentUpdateRequest requestParam);

    IPage<KnowledgeDocumentResponse> pageKnowledgeDocument(String kbId, KnowledgeDocumentPageRequest requestParam);

    void executeChunk(String docId);
}
