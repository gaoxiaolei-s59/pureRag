package org.puregxl.site.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.config.AIModelProperties;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseCreateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBasePageRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeBaseUpdateRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeBaseResponse;
import org.puregxl.site.knowledge.service.KnowledgeBaseService;
import org.puregxl.site.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.knowledge.service.resource.KnowledgeVectorResourceService;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeVectorResourceService vectorResourceService;
    private final KnowledgeStorageResourceService storageResourceService;
    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final AIModelProperties aiModelProperties;

    /**
     * 创建对应的KnowledgeBase - Milvus - Rustfs
     *
     * @param request 创建请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createKnowledgeBase(KnowledgeBaseCreateRequest request) {
        String collectionName = vectorResourceService.normalizeCollectionName(request.getCollectionName());
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

            // RustFS bucket 与 Milvus Collection 使用同一个资源名，便于知识库资源统一定位。
            storageResourceService.createStorage(collectionName);
            storageCreated = true;

            knowledgeBaseMapper.insert(KnowledgeBaseDO.builder()
                    .name(request.getName().trim())
                    .embeddingModel(request.getEmbeddingModel().trim())
                    .collectionName(collectionName)
                    .createdBy(UserContext.getUserContext().getUserId())
                    .build());
        } catch (RuntimeException ex) {
            if (storageCreated) {
                storageResourceService.rollbackStorage(collectionName);
            }
            if (collectionCreated) {
                vectorResourceService.rollbackCollection(collectionName);
            }
            throw ex;
        }
    }

    /**
     * 更改文档
     *
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

        if (!Objects.equals(knowledgeBaseDO.getCreatedBy(), UserContext.getUserContext().getUserId())) {
            throw new ClientException("错误的修改");
        }

        KnowledgeBaseDO build = KnowledgeBaseDO.builder()
                .id(kbId)
                .updatedBy(UserContext.getUserContext().getUserId())
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
     *
     * @param kbId
     */
    @Override
    public void delete(String kbId) {
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(kbId);

        if (ObjectUtils.isEmpty(knowledgeBaseDO)) {
            throw new ServiceException("知识库不存在");
        }

        if (!Objects.equals(UserContext.getUserContext().getUserId(), knowledgeBaseDO.getCreatedBy())) {
            throw new ServiceException("错误的删除");
        }


        LambdaQueryWrapper<KnowledgeDocumentDO> knowledgeDocumentEq = Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                .eq(KnowledgeDocumentDO::getKbId, kbId);

        List<KnowledgeDocumentDO> knowledgeDocumentDOS = knowledgeDocumentMapper.selectList(knowledgeDocumentEq);
        if (!knowledgeDocumentDOS.isEmpty()) {
            throw new ClientException("不能删除有文档的知识库");
        }

        knowledgeBaseDO.setDelFlag(1);
        knowledgeBaseDO.setUpdatedBy(UserContext.getUserContext().getUserId());
        knowledgeBaseMapper.updateById(knowledgeBaseDO);
        log.info("删除成功, kbId={}", kbId);

    }

    /**
     * 查询数据库基本信息
     *
     * @param kbId
     * @return
     */
    @Override
    public KnowledgeBaseResponse queryKnowledgeBaseById(String kbId) {
        if (!StringUtils.hasText(kbId)) {
            throw new ClientException("知识库 ID 不能为空");
        }

        LambdaQueryWrapper<KnowledgeDocumentDO> knowledgeDocumentEq =
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, kbId);

        Long documentCount = knowledgeDocumentMapper.selectCount(knowledgeDocumentEq);

        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(kbId);
        if (ObjectUtils.isEmpty(knowledgeBaseDO)) {
            throw new ServiceException("知识库不存在");
        }
        KnowledgeBaseResponse bean = BeanUtil.toBean(knowledgeBaseDO, KnowledgeBaseResponse.class);
        bean.setDocumentCount(documentCount);

        return bean;
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
        String name = requestParam == null ? null : requestParam.getName();
        String nameKeyword = StringUtils.hasText(name) ? name.trim() : null;

        Page<KnowledgeBaseDO> page = Page.of(current, size);
        IPage<KnowledgeBaseDO> resultPage = knowledgeBaseMapper.selectPage(page, Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                .eq(StringUtils.hasText(nameKeyword),
                        KnowledgeBaseDO::getName,
                        nameKeyword)
                .eq(KnowledgeBaseDO::getDelFlag, 0)
                .orderByDesc(KnowledgeBaseDO::getCreateTime));
        return resultPage.convert(t -> BeanUtil.toBean(t, KnowledgeBaseResponse.class));
    }

    /**
     * 查询获取的列表
     * @return
     */
    @Override
    public List<String> queryModels() {
        List<AIModelProperties.ModelCandidate> candidates = aiModelProperties.getEmbedding().getCandidates();

        return candidates.stream()
                .map(AIModelProperties.ModelCandidate::getModel)
                .toList();
    }


}
