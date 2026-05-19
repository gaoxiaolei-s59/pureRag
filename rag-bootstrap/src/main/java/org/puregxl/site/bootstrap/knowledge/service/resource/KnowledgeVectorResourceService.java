package org.puregxl.site.bootstrap.knowledge.service.resource;

import java.util.List;

/**
 * 知识库向量资源服务，负责校验并管理向量库 Collection 生命周期。
 */
public interface KnowledgeVectorResourceService {

    /**
     * 规范化并校验 Collection 名称。
     *
     * @param collectionName 原始 Collection 名称
     * @return 规范化后的 Collection 名称
     */
    String normalizeCollectionName(String collectionName);

    /**
     * 创建向量 Collection。
     *
     * @param collectionName Collection 名称
     */
    void createCollection(String collectionName);

    /**
     * 回滚向量 Collection。
     *
     * @param collectionName Collection 名称
     */
    void rollbackCollection(String collectionName);

    /**
     * 删除指定文档在向量库中的所有 Chunk。
     *
     * @param collectionName Collection 名称
     * @param docId 文档 ID
     */
    void deleteDocumentChunks(String collectionName, String docId);

    /**
     * 批量写入文档 Chunk 向量。
     *
     * @param collectionName Collection 名称
     * @param chunks Chunk 向量数据
     */
    void insertChunks(String collectionName, List<KnowledgeVectorChunk> chunks);

    /**
     * 向量库中的单条 Chunk 数据。
     */
    record KnowledgeVectorChunk(String chunkId,
                                String docId,
                                Integer chunkIndex,
                                String content,
                                List<Float> embedding) {
    }
}
