package org.puregxl.site.rag.retrieval.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.config.RagVectorProperties;
import org.puregxl.site.rag.retrieval.RagRetrievalService;
import org.puregxl.site.framework.exception.ServiceException;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Milvus 的 RAG 检索实现。
 * <p>
 * 只负责读路径召回：根据用户问题向量在指定 Collection 中搜索最相似的 Chunk，并转换为上层
 * pipeline 使用的 {@link RetrievedChunk}。Collection 生命周期、文档向量写入和清理仍由
 * knowledge.resource 负责，避免读写职责混在一个资源服务里。
 */
@Service
@RequiredArgsConstructor
public class MilvusRagRetrievalService implements RagRetrievalService {

    private static final String CHUNK_ID_FIELD = "chunk_id";
    private static final String DOC_ID_FIELD = "doc_id";
    private static final String CONTENT_FIELD = "content";
    private static final String EMBEDDING_FIELD = "embedding";

    private final RagVectorProperties ragVectorProperties;
    private final MilvusClientV2 milvusClient;

    @Override
    public List<RetrievedChunk> searchSimilarChunks(String collectionName, List<Float> queryEmbedding, int topK) {
        if (StrUtil.isBlank(collectionName) || CollUtil.isEmpty(queryEmbedding) || topK <= 0) {
            return List.of();
        }
        try {
            SearchResp searchResp = milvusClient.search(SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(EMBEDDING_FIELD)
                    .data(List.of(new FloatVec(queryEmbedding)))
                    .topK(topK)
                    .outputFields(List.of(CHUNK_ID_FIELD, DOC_ID_FIELD, CONTENT_FIELD))
                    .searchParams(Map.of("metric_type", ragVectorProperties.getMilvus().getMetricType()))
                    .consistencyLevel(ConsistencyLevel.STRONG)
                    .build());

            if (CollUtil.isEmpty(searchResp.getSearchResults())) {
                return List.of();
            }

            List<RetrievedChunk> chunks = new ArrayList<>();
            for (SearchResp.SearchResult result : searchResp.getSearchResults().get(0)) {
                Map<String, Object> entity = result.getEntity();
                chunks.add(RetrievedChunk.builder()
                        .id(Objects.toString(entity.get(CHUNK_ID_FIELD), ""))
                        .docId(Objects.toString(entity.get(DOC_ID_FIELD), ""))
                        .text(Objects.toString(entity.get(CONTENT_FIELD), ""))
                        .score(result.getScore())
                        .build());
            }
            return chunks;
        } catch (Exception ex) {
            throw new ServiceException("检索 Milvus 文档 Chunk 失败：" + ex.getMessage());
        }
    }
}
