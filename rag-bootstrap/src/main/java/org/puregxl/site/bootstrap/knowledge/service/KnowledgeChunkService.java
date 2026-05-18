package org.puregxl.site.bootstrap.knowledge.service;

import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeChunkBatchRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeChunkCreateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeChunkUpdateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.response.KnowledgeChunkResponse;

public interface KnowledgeChunkService {

    KnowledgeChunkResponse createKnowledgeChunk(String docId, KnowledgeChunkCreateRequest request);

    void updateKnowledgeChunk(String docId, String chunkId, KnowledgeChunkUpdateRequest request);

    void delete(String docId, String chunkId);

    void enableChunk(String docId, String chunkId, boolean enabled);

    void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest request, boolean enabled);
}
