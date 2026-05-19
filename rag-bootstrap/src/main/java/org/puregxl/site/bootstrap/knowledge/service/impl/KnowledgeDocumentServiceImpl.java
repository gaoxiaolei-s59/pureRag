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
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentScheduleExecDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentScheduleExecMapper;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeDocumentPageRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeDocumentUpdateRequest;
import org.puregxl.site.bootstrap.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.site.bootstrap.knowledge.dto.response.KnowledgeDocumentResponse;
import org.puregxl.site.bootstrap.knowledge.enums.DocumentStatus;
import org.puregxl.site.bootstrap.knowledge.enums.SourceType;
import org.puregxl.site.bootstrap.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.bootstrap.user.context.UserContext;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.exception.ServiceException;
import org.puregxl.site.framework.mq.productor.MessageQueueProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Date;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    private final KnowledgeStorageResourceService storageResourceService;

    private final MessageQueueProducer messageQueueProducer;

    private final KnowledgeDocumentScheduleExecMapper scheduleExecMapper;

    @Value("knowledge-document-chunk_topic${unique-name:}")
    private String chunkTopic = "knowledge-document-chunk_topic";
    
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

        MultipartFile uploadFile = buildUploadFile(sourceType, requestParam, file);
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
                .fileType(resolveFileType(uploadFile, docName))
                .fileSize(uploadFile.getSize())
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

        String bucketName = knowledgeBaseDO.getCollectionName();
        String objectKey = buildDocumentObjectKey(document.getId(), uploadFile.getOriginalFilename());
        String fileUrl = storageResourceService.uploadDocument(bucketName, objectKey, uploadFile);
        KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                .id(document.getId())
                .fileUrl(fileUrl)
                .updatedBy(currentUserId())
                .build();
        int updated = knowledgeDocumentMapper.updateById(update);
        if (updated != 1) {
            storageResourceService.deleteDocument(bucketName, objectKey);
            throw new ServiceException("更新文档文件地址失败");
        }
        document.setFileUrl(fileUrl);
        return BeanUtil.toBean(document, KnowledgeDocumentResponse.class);
    }

    /**
     * 开始文档分块：先发送 RocketMQ 事务消息，再在本地事务里标记文档处理中并记录一次执行流水。
     */
    @Override
    public void startChunkKnowledgeDocument(String docId) {
        KnowledgeDocumentDO document = getDocumentDO(docId);
        if (StrUtil.isNotBlank(document.getProcessMode()) && !"chunk".equalsIgnoreCase(document.getProcessMode())) {
            throw new ClientException("当前接口仅支持 chunk 处理模式");
        }

        KnowledgeDocumentChunkEvent event = KnowledgeDocumentChunkEvent.builder()
                .docId(document.getId())
                .kbId(document.getKbId())
                .operator(currentUserId())
                .build();
        messageQueueProducer.sendInTransaction(chunkTopic,
                document.getId(),
                "文档分块任务",
                event,
                ignored -> startChunkLocalTransaction(document));
    }

    private void startChunkLocalTransaction(KnowledgeDocumentDO document) {
        // RocketMQ 事务消息的本地事务：只有文档状态和执行流水同时写入成功，消息才提交给消费者。
        KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                .id(document.getId())
                .status(DocumentStatus.RUNNING.getCode())
                .updatedBy(currentUserId())
                .build();
        int updated = knowledgeDocumentMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("开始文档分块失败");
        }
        KnowledgeDocumentScheduleExecDO exec = KnowledgeDocumentScheduleExecDO.builder()
                .scheduleId(null)
                .docId(document.getId())
                .kbId(document.getKbId())
                .status(DocumentStatus.RUNNING.getCode())
                .message("手动触发文档分块")
                .startTime(new Date())
                .fileName(document.getDocName())
                .fileSize(document.getFileSize())
                .build();
        int inserted = scheduleExecMapper.insert(exec);
        if (inserted != 1) {
            throw new ServiceException("创建文档分块执行记录失败");
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

    /**
     * 更新文档接口
     * @param docId
     * @param requestParam
     */
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

    /**
     * 开始切块
     * @param docId
     */
    @Override
    public void executeChunk(String docId) {

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

    private MultipartFile buildUploadFile(String sourceType, KnowledgeDocumentUploadRequest requestParam, MultipartFile file) {
        if (SourceType.FILE.getCode().equals(sourceType)) {
            return file;
        }
        return downloadUrlFile(requestParam.getSourceLocation().trim());
    }

    private MultipartFile downloadUrlFile(String sourceLocation) {
        try (InputStream inputStream = URI.create(sourceLocation).toURL().openStream()) {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                throw new ClientException("URL 文档内容为空");
            }
            String filename = resolveUrlFilename(sourceLocation);
            String contentType = defaultIfBlank(URLConnection.guessContentTypeFromName(filename), "application/octet-stream");
            return new UrlMultipartFile("file", filename, contentType, bytes);
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ServiceException("拉取 URL 文档失败：" + ex.getMessage());
        }
    }

    private String resolveUrlFilename(String sourceLocation) {
        URI uri = URI.create(sourceLocation);
        String path = uri.getPath();
        if (StrUtil.isBlank(path) || path.endsWith("/")) {
            return "url-document";
        }
        String filename = path.substring(path.lastIndexOf('/') + 1);
        return StrUtil.isBlank(filename) ? "url-document" : filename;
    }

    private String resolveFileType(MultipartFile file, String docName) {
        String filename = file == null ? docName : file.getOriginalFilename();
        if (StrUtil.isBlank(filename) || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private String buildDocumentObjectKey(String docId, String docName) {
        String filename = docName.replace("\\", "_").replace("/", "_");
        return "docs/" + docId + "/" + filename;
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

    private record UrlMultipartFile(String name, String originalFilename, String contentType, byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("URL 文档上传流程不需要 transferTo");
        }
    }
}
