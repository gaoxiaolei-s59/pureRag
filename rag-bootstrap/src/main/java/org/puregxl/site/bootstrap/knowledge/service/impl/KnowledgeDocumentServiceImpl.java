package org.puregxl.site.bootstrap.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeChunkDO;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeDocumentPageRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeDocumentUpdateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.site.bootstrap.knowledge.dto.response.KnowledgeDocumentResponse;
import org.puregxl.site.bootstrap.knowledge.enums.DocumentStatus;
import org.puregxl.site.bootstrap.knowledge.enums.SourceType;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.bootstrap.user.context.UserContext;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    /**
     * 上传文档：入库记录 + 文件落盘，返回文档ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentResponse uploadKnowledgeDocument(String kbId, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        if (StrUtil.isBlank(kbId)) {
            throw new ClientException("知识库 ID 不能为空");
        }
        if (requestParam == null) {
            throw new ClientException("文档上传参数不能为空");
        }

        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(kbId);

        if (knowledgeBaseDO == null) {
            throw new ClientException("知识库不存在");
        }

        String sourceType = normalizeSourceType(requestParam.getSourceType(), file);
        if (SourceType.FILE.getCode().equals(sourceType) && (file == null || file.isEmpty())) {
            throw new ClientException("文件上传类型必须提供文件");
        }
        if (SourceType.URL.getCode().equals(sourceType) && StrUtil.isBlank(requestParam.getSourceLocation())) {
            throw new ClientException("URL 来源必须提供来源地址");
        }

        String docName = buildDocumentName(sourceType, requestParam, file);
        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .kbId(kbId)
                .docName(docName)
                .sourceType(sourceType)
                .sourceLocation(requestParam.getSourceLocation())
                .scheduleEnabled(toFlag(requestParam.getScheduleEnabled()))
                .scheduleCron(requestParam.getScheduleCron())
                .enabled(1)
                .chunkCount(0)
                .fileUrl(SourceType.FILE.getCode().equals(sourceType) ? file.getOriginalFilename() : null)
                .fileType(resolveFileType(file, docName))
                .fileSize(file == null ? null : file.getSize())
                .processMode(defaultIfBlank(requestParam.getProcessMode(), "chunk"))
                .chunkStrategy(requestParam.getChunkStrategy())
                .chunkConfig(requestParam.getChunkConfig())
                .pipelineId(requestParam.getPipelineId())
                .status(DocumentStatus.PENDING.getCode())
                .createdBy(currentUserId())
                .deleted(0)
                .build();
        int inserted = knowledgeDocumentMapper.insert(document);
        if (inserted != 1) {
            throw new ServiceException("上传文档失败");
        }
        return BeanUtil.toBean(document, KnowledgeDocumentResponse.class);
    }

    /**
     * 开始文档分块。当前先完成状态流转，后续接入解析/向量化任务时从 running 状态继续处理。
     */
    @Override
    public void startChunkKnowledgeDocument(String docId) {
        KnowledgeDocumentDO document = getDocumentDO(docId);
        KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                .id(document.getId())
                .status(DocumentStatus.RUNNING.getCode())
                .updatedBy(currentUserId())
                .build();
        int updated = knowledgeDocumentMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("开始文档分块失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeDocument(String docId) {
        KnowledgeDocumentDO document = getDocumentDO(docId);

        KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                .id(document.getId())
                .updatedBy(currentUserId())
                .deleted(1)
                .build();
        int updated = knowledgeDocumentMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("删除文档失败");
        }

        // 删除文档时同步逻辑删除其下 chunk，避免列表和检索侧继续读到孤立分块。
        knowledgeChunkMapper.update(null, Wrappers.lambdaUpdate(KnowledgeChunkDO.class)
                .eq(KnowledgeChunkDO::getDocId, docId)
                .set(KnowledgeChunkDO::getDeleted, 1)
                .set(KnowledgeChunkDO::getUpdatedBy, currentUserId()));
    }

    @Override
    public KnowledgeDocumentResponse getKnowledgeDocument(String docId) {
        return BeanUtil.toBean(getDocumentDO(docId), KnowledgeDocumentResponse.class);
    }

    @Override
    public void updateKnowledgeDocument(String docId, KnowledgeDocumentUpdateRequest requestParam) {
        if (requestParam == null) {
            throw new ClientException("文档更新参数不能为空");
        }
        KnowledgeDocumentDO document = getDocumentDO(docId);

        KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                .id(document.getId())
                .docName(StrUtil.isBlank(requestParam.getDocName()) ? null : requestParam.getDocName().trim())
                .enabled(requestParam.getEnabled() == null ? null : toFlag(requestParam.getEnabled()))
                .scheduleEnabled(requestParam.getScheduleEnabled() == null ? null : toFlag(requestParam.getScheduleEnabled()))
                .scheduleCron(requestParam.getScheduleCron())
                .chunkStrategy(requestParam.getChunkStrategy())
                .chunkConfig(requestParam.getChunkConfig())
                .updatedBy(currentUserId())
                .build();
        int updated = knowledgeDocumentMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("更新文档失败");
        }
    }

    @Override
    public IPage<KnowledgeDocumentResponse> pageKnowledgeDocument(String kbId, KnowledgeDocumentPageRequest requestParam) {
        if (StrUtil.isBlank(kbId)) {
            throw new ClientException("知识库 ID 不能为空");
        }

        long current = requestParam == null || requestParam.getCurrent() <= 0 ? 1 : requestParam.getCurrent();
        long size = requestParam == null || requestParam.getSize() <= 0 ? 10 : requestParam.getSize();

        IPage<KnowledgeDocumentDO> resultPage = knowledgeDocumentMapper.selectPage(Page.of(current, size),
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, kbId)
                        .like(requestParam != null && StrUtil.isNotBlank(requestParam.getKeyword()),
                                KnowledgeDocumentDO::getDocName,
                                requestParam == null ? null : requestParam.getKeyword().trim())
                        .eq(requestParam != null && StrUtil.isNotBlank(requestParam.getStatus()),
                                KnowledgeDocumentDO::getStatus,
                                requestParam == null ? null : requestParam.getStatus().trim())
                        .orderByDesc(KnowledgeDocumentDO::getCreateTime));
        return resultPage.convert(item -> BeanUtil.toBean(item, KnowledgeDocumentResponse.class));
    }

    private KnowledgeDocumentDO getDocumentDO(String docId) {
        if (StrUtil.isBlank(docId)) {
            throw new ClientException("文档 ID 不能为空");
        }
        KnowledgeDocumentDO document = knowledgeDocumentMapper.selectById(docId);
        if (document == null) {
            throw new ClientException("文档不存在");
        }
        return document;
    }

    private String normalizeSourceType(String sourceType, MultipartFile file) {
        if (StrUtil.isBlank(sourceType)) {
            return file == null ? SourceType.URL.getCode() : SourceType.FILE.getCode();
        }
        String normalized = sourceType.trim().toLowerCase(Locale.ROOT);
        if (!SourceType.FILE.getCode().equals(normalized) && !SourceType.URL.getCode().equals(normalized)) {
            throw new ClientException("文档来源类型只支持 file 或 url");
        }
        return normalized;
    }

    private String buildDocumentName(String sourceType, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        if (SourceType.FILE.getCode().equals(sourceType)) {
            String originalFilename = file.getOriginalFilename();
            if (StrUtil.isBlank(originalFilename)) {
                throw new ClientException("文件名不能为空");
            }
            return originalFilename.trim();
        }
        return requestParam.getSourceLocation().trim();
    }

    private String resolveFileType(MultipartFile file, String docName) {
        String filename = file == null ? docName : file.getOriginalFilename();
        if (StrUtil.isBlank(filename) || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StrUtil.isBlank(value) ? defaultValue : value.trim();
    }

    private Integer toFlag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private String currentUserId() {
        return UserContext.getUserContext() == null ? null : UserContext.getUserContext().getUserId();
    }
}
