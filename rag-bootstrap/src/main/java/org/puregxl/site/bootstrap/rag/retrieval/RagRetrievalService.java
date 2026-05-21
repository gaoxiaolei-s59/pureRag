package org.puregxl.site.bootstrap.rag.retrieval;

import org.puregxl.site.infra.framework.convention.RetrievedChunk;

import java.util.List;

/**
 * RAG 检索服务，负责把查询向量转换为可喂给大模型的候选 Chunk。
 * <p>
 * 该接口属于问答检索链路，和 knowledge.resource 的 Collection 创建、写入、清理职责分离，
 * 后续扩展 hybrid search、知识库过滤、score threshold、metadata filter 时都收敛在这里。
 */
public interface RagRetrievalService {

    /**
     * 根据查询向量召回相似 Chunk。
     *
     * @param collectionName Collection 名称
     * @param queryEmbedding 查询文本向量
     * @param topK           最大召回数量
     * @return 与问题最相似的候选 Chunk 列表
     */
    List<RetrievedChunk> searchSimilarChunks(String collectionName, List<Float> queryEmbedding, int topK);
}
