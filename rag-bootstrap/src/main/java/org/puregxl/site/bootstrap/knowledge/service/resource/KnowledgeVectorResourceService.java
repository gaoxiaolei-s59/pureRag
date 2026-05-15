package org.puregxl.site.bootstrap.knowledge.service.resource;

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
}
