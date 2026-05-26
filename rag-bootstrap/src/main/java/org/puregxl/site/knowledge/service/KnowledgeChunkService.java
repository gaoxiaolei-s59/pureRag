package org.puregxl.site.knowledge.service;

import org.puregxl.site.knowledge.dto.request.KnowledgeChunkBatchRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeChunkCreateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeChunkUpdateRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeChunkResponse;

public interface KnowledgeChunkService {

    java.util.List<KnowledgeChunkResponse> listKnowledgeChunks(String docId);

    KnowledgeChunkResponse createKnowledgeChunk(String docId, KnowledgeChunkCreateRequest request);

    void updateKnowledgeChunk(String docId, String chunkId, KnowledgeChunkUpdateRequest request);

    void delete(String docId, String chunkId);

    void enableChunk(String docId, String chunkId, boolean enabled);

    void batchToggleEnabled(String docId, KnowledgeChunkBatchRequest request, boolean enabled);
}
