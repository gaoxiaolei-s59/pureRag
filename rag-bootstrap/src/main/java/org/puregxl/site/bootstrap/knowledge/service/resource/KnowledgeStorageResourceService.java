package org.puregxl.site.bootstrap.knowledge.service.resource;

/**
 * 知识库对象存储资源服务，负责把业务资源名映射为对象存储资源并管理生命周期。
 */
public interface KnowledgeStorageResourceService {

    /**
     * 根据知识库资源名生成对象存储 bucket 名称。
     *
     * @param resourceName 业务资源名，通常为 Milvus Collection 名称
     * @return 符合对象存储命名规则的 bucket 名称
     */
    String buildBucketName(String resourceName);

    /**
     * 创建对象存储资源。
     *
     * @param bucketName bucket 名称
     */
    void createStorage(String bucketName);

    /**
     * 回滚对象存储资源。
     *
     * @param bucketName bucket 名称
     */
    void rollbackStorage(String bucketName);
}
