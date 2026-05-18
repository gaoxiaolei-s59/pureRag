package org.puregxl.site.bootstrap.knowledge.service.resource;

import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库对象存储资源服务，负责管理知识库文档文件的存储生命周期。
 */
public interface KnowledgeStorageResourceService {

    /**
     * 创建对象存储资源。
     *
     * @param bucketName bucket 名称
     */
    void createStorage(String bucketName);

    /**
     * 上传文档文件。
     *
     * @param bucketName bucket 名称
     * @param objectKey 对象 key
     * @param file 待上传文件
     * @return RustFS 控制台浏览地址，格式为 {consoleUrl}/browser/{bucket}/{encodedKey}
     */
    String uploadDocument(String bucketName, String objectKey, MultipartFile file);

    /**
     * 读取已经上传的文档文件。
     *
     * @param fileUrl RustFS 控制台浏览地址，格式为 {consoleUrl}/browser/{bucket}/{encodedKey}
     * @return 文件二进制内容
     */
    byte[] downloadDocument(String fileUrl);

    /**
     * 删除文档文件。
     *
     * @param bucketName bucket 名称
     * @param objectKey 对象 key
     */
    void deleteDocument(String bucketName, String objectKey);

    /**
     * 回滚对象存储资源。
     *
     * @param bucketName bucket 名称
     */
    void rollbackStorage(String bucketName);
}
