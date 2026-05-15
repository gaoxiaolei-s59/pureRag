package org.puregxl.site.bootstrap.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeBaseCreateRequest;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeBaseService;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeVectorResourceService;
import org.puregxl.site.framework.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeVectorResourceService vectorResourceService;
    private final KnowledgeStorageResourceService storageResourceService;

    /**
     * 创建对应的KnowledgeBase - Milvus - Rustfs
     * @param request 创建请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createKnowledgeBase(KnowledgeBaseCreateRequest request) {
        String collectionName = vectorResourceService.normalizeCollectionName(request.getCollectionName());
        String storageBucketName = storageResourceService.buildBucketName(collectionName);
        Long count = knowledgeBaseMapper.selectCount(Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .eq(KnowledgeBaseDO::getName, request.getName().trim())
                .or()
                .eq(KnowledgeBaseDO::getCollectionName, collectionName));
        if (count != null && count > 0) {
            throw new ServiceException("知识库名称或向量集合已存在，禁止重复创建");
        }

        boolean collectionCreated = false;
        boolean storageCreated = false;
        try {
            // 创建和知识库绑定的 Milvus Collection。
            vectorResourceService.createCollection(collectionName);
            collectionCreated = true;

            // RustFS bucket 使用 S3 兼容命名规则，不能直接复用带下划线的 Milvus Collection 名称。
            storageResourceService.createStorage(storageBucketName);
            storageCreated = true;

            knowledgeBaseMapper.insert(KnowledgeBaseDO.builder()
                    .name(request.getName().trim())
                    .embeddingModel(request.getEmbeddingModel().trim())
                    .collectionName(collectionName)
                    .build());
        } catch (RuntimeException ex) {
            if (storageCreated) {
                storageResourceService.rollbackStorage(storageBucketName);
            }
            if (collectionCreated) {
                vectorResourceService.rollbackCollection(collectionName);
            }
            throw ex;
        }
    }
}
