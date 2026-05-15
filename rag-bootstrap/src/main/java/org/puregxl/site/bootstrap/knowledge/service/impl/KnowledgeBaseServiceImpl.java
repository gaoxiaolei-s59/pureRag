package org.puregxl.site.bootstrap.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeBaseCreateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeBasePageRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeBaseUpdateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.response.KnowledgeBaseResponse;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeBaseService;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeVectorResourceService;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
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

    /**
     * 更改文档
     * @param kbId
     * @param request
     */
    @Override
    public void renameKnowledgeBase(String kbId, KnowledgeBaseUpdateRequest request) {
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(kbId);

        if (ObjectUtils.isEmpty(knowledgeBaseDO)) {
            throw new ServiceException("知识库不存在");
        }

        if (!StringUtils.hasText(request.getName())) {
            throw new ClientException("知识库名称不能为空");
        }

        KnowledgeBaseDO build = KnowledgeBaseDO.builder()
                .id(kbId)
                .name(request.getName()).build();
        int updateById = knowledgeBaseMapper.updateById(build);

        if (updateById != 1) {
            log.info("修改知识库失败, kbId:{}", kbId);
            throw new ServiceException("修改知识库失败");
        }

        log.info("成功重命名知识库, kbId={}, newName={}", kbId, request.getName());
    }

    /**
     * 删除知识库
     * @param kbId
     */
    @Override
    public void delete(String kbId) {
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(kbId);

        if (ObjectUtils.isEmpty(knowledgeBaseDO)) {
            throw new ServiceException("知识库不存在");
        }

        //TODO 扩展点 - 后续可以更改为有文档就不支持删除
        int deleted = knowledgeBaseMapper.deleteById(kbId);

       if (deleted != 1) {
           log.info("删除失败, kbId={}", kbId);
           throw new ServiceException("删除失败");
       }

        log.info("删除成功, kbId={}", kbId);

    }

    /**
     * 查询数据库基本信息
     * @param kbId
     * @return
     */
    @Override
    public KnowledgeBaseResponse queryKnowledgeBaseById(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new ClientException("知识库 ID 不能为空");
        }
        //TODO 文档数量
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(kbId);
        if (ObjectUtils.isEmpty(knowledgeBaseDO)) {
            throw new ServiceException("知识库不存在");
        }
        return BeanUtil.toBean(knowledgeBaseDO,  KnowledgeBaseResponse.class);
    }

    /**
     * 分页查询知识库列表
     *
     * @param requestParam 分页查询参数
     * @return 知识库分页结果
     */
    @Override
    public IPage<KnowledgeBaseResponse> pageQuery(KnowledgeBasePageRequest requestParam) {
        long current = requestParam == null || requestParam.getCurrent() <= 0 ? 1 : requestParam.getCurrent();
        long size = requestParam == null || requestParam.getSize() <= 0 ? 10 : requestParam.getSize();

        Page<KnowledgeBaseDO> page = Page.of(current, size);
        IPage<KnowledgeBaseDO> resultPage = knowledgeBaseMapper.selectPage(page, Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .like(requestParam != null && StringUtils.hasText(requestParam.getName()),
                        KnowledgeBaseDO::getName,
                        requestParam == null ? null : requestParam.getName().trim())
                .orderByDesc(KnowledgeBaseDO::getCreateTime));

        return resultPage.convert(t -> BeanUtil.toBean(t,  KnowledgeBaseResponse.class));
    }

}
