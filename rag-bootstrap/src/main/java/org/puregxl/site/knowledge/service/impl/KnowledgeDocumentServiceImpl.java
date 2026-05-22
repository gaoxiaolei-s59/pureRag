package org.puregxl.site.knowledge.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeChunkDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentScheduleDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentScheduleExecDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentScheduleExecMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentScheduleMapper;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentPageRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentUpdateRequest;
import org.puregxl.site.knowledge.dto.request.KnowledgeDocumentUploadRequest;
import org.puregxl.site.knowledge.dto.response.KnowledgeDocumentResponse;
import org.puregxl.site.knowledge.enums.DocumentStatus;
import org.puregxl.site.knowledge.enums.SourceType;
import org.puregxl.site.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import org.puregxl.site.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.knowledge.service.resource.FileParseService;
import org.puregxl.site.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.knowledge.service.resource.KnowledgeVectorResourceService;
import org.puregxl.site.knowledge.util.KnowledgeChunkHashUtils;
import org.puregxl.site.knowledge.util.KnowledgeContentHashUtils;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.exception.ServiceException;
import org.puregxl.site.framework.mq.productor.MessageQueueProducer;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private static final String MANUAL_SCHEDULE_ID = "manual";

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    private final KnowledgeStorageResourceService storageResourceService;

    private final FileParseService fileParseService;

    private final KnowledgeVectorResourceService vectorResourceService;

    private final MessageQueueProducer messageQueueProducer;

    private final KnowledgeDocumentScheduleExecMapper scheduleExecMapper;

    private final KnowledgeDocumentScheduleMapper scheduleMapper;

    private final EmbeddingService embeddingService;

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
        String documentContentHash = buildDocumentContentHash(uploadFile);
        String docName = buildDocumentName(sourceType, requestParam, file);
        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .kbId(kbId)
                .docName(docName)
                .sourceType(sourceType)
                .sourceLocation(requestParam.getSourceLocation())
                .scheduleEnabled(toFlag(requestParam.getScheduleEnabled()))
                .contentHash(documentContentHash)
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

        syncDocumentSchedule(document);
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
                .scheduleId(MANUAL_SCHEDULE_ID)
                .docId(document.getId())
                .kbId(document.getKbId())
                .status(DocumentStatus.RUNNING.getCode())
                .message("手动触发文档分块")
                .startTime(new Date())
                .fileName(document.getDocName())
                .fileSize(document.getFileSize())
                .contentHash(document.getContentHash())
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
        disableDocumentSchedule(docId);

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
        KnowledgeDocumentDO scheduleDocument = KnowledgeDocumentDO.builder()
                .id(document.getId())
                .kbId(document.getKbId())
                .scheduleEnabled(requestParam.getScheduleEnabled() == null
                        ? document.getScheduleEnabled()
                        : toFlag(requestParam.getScheduleEnabled()))
                .scheduleCron(requestParam.getScheduleCron() == null
                        ? document.getScheduleCron()
                        : requestParam.getScheduleCron())
                .build();
        syncDocumentSchedule(scheduleDocument);
    }

    @Override
    public IPage<KnowledgeDocumentResponse> pageKnowledgeDocument(String kbId, KnowledgeDocumentPageRequest requestParam) {
        if (StrUtil.isBlank(kbId)) {
            throw new ClientException("知识库 ID 不能为空");
        }

        long current = requestParam == null || requestParam.getCurrent() <= 0 ? 1 : requestParam.getCurrent();
        long size = requestParam == null || requestParam.getSize() <= 0 ? 10 : requestParam.getSize();
        String keyword = requestParam == null ? null : requestParam.getKeyword();
        String status = requestParam == null ? null : requestParam.getStatus();
        String keywordParam = StrUtil.isBlank(keyword) ? null : keyword.trim();
        String statusParam = StrUtil.isBlank(status) ? null : status.trim();

        IPage<KnowledgeDocumentDO> resultPage = knowledgeDocumentMapper.selectPage(Page.of(current, size),
                Wrappers.lambdaQuery(KnowledgeDocumentDO.class)
                        .eq(KnowledgeDocumentDO::getKbId, kbId)
                        .like(StrUtil.isNotBlank(keywordParam),
                                KnowledgeDocumentDO::getDocName,
                                keywordParam)
                        .eq(StrUtil.isNotBlank(statusParam),
                                KnowledgeDocumentDO::getStatus,
                                statusParam)
                        .orderByDesc(KnowledgeDocumentDO::getCreateTime));
        return resultPage.convert(item -> BeanUtil.toBean(item, KnowledgeDocumentResponse.class));
    }

    /**
     * 开始切块
     * @param docId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void executeChunk(String docId) {
        KnowledgeDocumentDO document = getDocumentDO(docId);
        try {
            if (StrUtil.isBlank(document.getFileUrl())) {
                document.setStatus(DocumentStatus.FAILED.getCode());
                knowledgeDocumentMapper.updateById(document);
                throw new ClientException("文档文件地址不能为空");

            }
            String text = fileParseService.parseFileByTika(document.getFileUrl());
            if (StrUtil.isBlank(text)) {
                document.setStatus(DocumentStatus.FAILED.getCode());
                knowledgeDocumentMapper.updateById(document);
                throw new ClientException("文档解析内容为空");
            }
            KnowledgeBaseDO knowledgeBase = getKnowledgeBase(document);

            String embeddingModel = knowledgeBase.getEmbeddingModel();
            ChunkConfig chunkConfig = parseChunkConfig(document);
            List<String> chunks = splitTextIntoChunks(text, chunkConfig);
            if (chunks.isEmpty()) {
                document.setStatus(DocumentStatus.FAILED.getCode());
                knowledgeDocumentMapper.updateById(document);
                throw new ClientException("文档解析内容为空");
            }
            List<List<Float>> embeddings = embeddingService.embedBatch(chunks, embeddingModel);
            if (embeddings.size() != chunks.size()) {
                document.setStatus(DocumentStatus.FAILED.getCode());
                knowledgeDocumentMapper.updateById(document);
                throw new ServiceException("Embedding 结果数量与 Chunk 数量不一致");
            }

            // 重新分块时先清理向量库旧数据，再废弃 MySQL 旧 Chunk，保证检索侧不会读到同文档旧版本内容。
            vectorResourceService.deleteDocumentChunks(knowledgeBase.getCollectionName(), document.getId());

            knowledgeChunkMapper.update(null, new UpdateWrapper<KnowledgeChunkDO>()
                    .eq("doc_id", document.getId())
                    .set("deleted", 1)
                    .set("updated_by", currentUserId()));

            List<KnowledgeVectorResourceService.KnowledgeVectorChunk> vectorChunks = new ArrayList<>(chunks.size());
            int insertedChunkCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String content = chunks.get(i);
                String contentHash = KnowledgeChunkHashUtils.sha256(content);
                if (existsContentHash(document.getId(), contentHash)) {
                    log.info("[文档分块] 跳过重复 Chunk，docId={}, chunkIndex={}, contentHash={}",
                            document.getId(), i, contentHash);
                    continue;
                }
                KnowledgeChunkDO chunk = KnowledgeChunkDO.builder()
                        .id(IdWorker.getIdStr())
                        .kbId(document.getKbId())
                        .docId(document.getId())
                        .chunkIndex(i)
                        .content(content)
                        .contentHash(contentHash)
                        .charCount(content.length())
                        .enabled(1)
                        .createdBy(currentUserId())
                        .deleted(0)
                        .build();
                int inserted = knowledgeChunkMapper.insert(chunk);
                if (inserted != 1) {
                    throw new ServiceException("写入文档 Chunk 失败");
                }
                insertedChunkCount++;
                vectorChunks.add(new KnowledgeVectorResourceService.KnowledgeVectorChunk(
                        chunk.getId(),
                        document.getId(),
                        i,
                        content,
                        embeddings.get(i)));
            }
            vectorResourceService.insertChunks(knowledgeBase.getCollectionName(), vectorChunks);

            KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                    .id(document.getId())
                    .status(DocumentStatus.SUCCESS.getCode())
                    .chunkCount(insertedChunkCount)
                    .updatedBy(currentUserId())
                    .build();

            int updated = knowledgeDocumentMapper.updateById(update);
            if (updated != 1) {
                throw new ServiceException("更新文档分块状态失败");
            }
        } catch (ClientException | ServiceException ex) {
            markDocumentChunkFailed(document.getId());
            throw ex;
        } catch (Exception ex) {
            markDocumentChunkFailed(document.getId());
            throw new ServiceException("执行文档分块失败：" + ex.getMessage());
        }

    }


    /**
     * 查询文档所属知识库，用于获取向量 Collection 和 embedding 模型。
     */
    private KnowledgeBaseDO getKnowledgeBase(KnowledgeDocumentDO document) {
        KnowledgeBaseDO knowledgeBaseDO = knowledgeBaseMapper.selectById(document.getKbId());
        if (knowledgeBaseDO == null) {
            throw new ClientException("查询知识库为空");
        }
        return knowledgeBaseDO;
    }

    /**
     *
     * @param document
     * @return
     */
    private ChunkConfig parseChunkConfig(KnowledgeDocumentDO document) {
        String configJson = StrUtil.isBlank(document.getChunkConfig()) ? document.getChunkStrategy() : document.getChunkConfig();
        int chunkSize = 1000;
        int overlapSize = 100;
        if (StrUtil.isBlank(configJson) || !configJson.trim().startsWith("{")) {
            return new ChunkConfig(chunkSize, overlapSize);
        }

        try {
            JsonObject config = JsonParser.parseString(configJson).getAsJsonObject();
            chunkSize = readPositiveInt(config, chunkSize, "chunkSize", "targetChars", "maxChars");
            overlapSize = readNonNegativeInt(config, overlapSize, "overlapSize", "overlapChars");
        } catch (JsonSyntaxException | IllegalStateException ex) {
            throw new ClientException("分块参数 JSON 格式错误");
        }
        if (overlapSize >= chunkSize) {
            throw new ClientException("分块重叠大小必须小于分块大小");
        }
        return new ChunkConfig(chunkSize, overlapSize);
    }

    private int readPositiveInt(JsonObject config, int defaultValue, String... names) {
        int value = readInt(config, defaultValue, names);
        if (value <= 0) {
            throw new ClientException("分块大小必须大于 0");
        }
        return value;
    }

    private int readNonNegativeInt(JsonObject config, int defaultValue, String... names) {
        int value = readInt(config, defaultValue, names);
        if (value < 0) {
            throw new ClientException("分块重叠大小不能小于 0");
        }
        return value;
    }

    private int readInt(JsonObject config, int defaultValue, String... names) {
        for (String name : names) {
            JsonElement element = config.get(name);
            if (element != null && !element.isJsonNull()) {
                return element.getAsInt();
            }
        }
        return defaultValue;
    }

    private List<String> splitTextIntoChunks(String text, ChunkConfig config) {
        String normalizedText = text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalizedText.length()) {
            int end = Math.min(start + config.chunkSize(), normalizedText.length());
            String chunk = normalizedText.substring(start, end).trim();
            if (StrUtil.isNotBlank(chunk)) {
                chunks.add(chunk);
            }
            if (end >= normalizedText.length()) {
                break;
            }
            start = end - config.overlapSize();
        }
        return chunks;
    }

    private void markDocumentChunkFailed(String docId) {
        knowledgeDocumentMapper.updateById(KnowledgeDocumentDO.builder()
                .id(docId)
                .status(DocumentStatus.FAILED.getCode())
                .updatedBy(currentUserId())
                .build());
    }

    /**
     * 判断当前文档是否已经存在相同内容哈希的 Chunk。
     * <p>
     * executeChunk 会先逻辑删除旧 Chunk，所以这里主要用于本次切块过程中去掉重复内容，避免相同段落重复进入
     * MySQL 和 Milvus。
     */
    private boolean existsContentHash(String docId, String contentHash) {
        Long count = knowledgeChunkMapper.selectCount(new QueryWrapper<KnowledgeChunkDO>()
                .eq("doc_id", docId)
                .eq("content_hash", contentHash));
        return count != null && count > 0;
    }

    /**
     * 同步文档定时任务配置。
     * <p>
     * 文档表保存用户配置，schedule 表保存调度运行状态。上传/更新文档时在同一个本地事务内同步 schedule，
     * 这样调度器只需要扫描 schedule 表，不必每轮重新解释所有文档配置。
     */
    private void syncDocumentSchedule(KnowledgeDocumentDO document) {
        if (!Integer.valueOf(1).equals(document.getScheduleEnabled()) || StrUtil.isBlank(document.getScheduleCron())) {
            disableDocumentSchedule(document.getId());
            return;
        }
        Date nextRunTime = nextRunTime(document.getScheduleCron(), new Date());
        KnowledgeDocumentScheduleDO existing = scheduleMapper.selectOne(new QueryWrapper<KnowledgeDocumentScheduleDO>()
                .eq("doc_id", document.getId())
                .last("limit 1"));
        if (existing == null) {
            KnowledgeDocumentScheduleDO schedule = KnowledgeDocumentScheduleDO.builder()
                    .docId(document.getId())
                    .kbId(document.getKbId())
                    .cronExpr(document.getScheduleCron().trim())
                    .enabled(1)
                    .nextRunTime(nextRunTime)
                    .build();
            int inserted = scheduleMapper.insert(schedule);
            if (inserted != 1) {
                throw new ServiceException("创建文档定时任务失败");
            }
            return;
        }
        KnowledgeDocumentScheduleDO update = KnowledgeDocumentScheduleDO.builder()
                .id(existing.getId())
                .kbId(document.getKbId())
                .cronExpr(document.getScheduleCron().trim())
                .enabled(1)
                .nextRunTime(nextRunTime)
                .lastError(null)
                .build();
        int updated = scheduleMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("更新文档定时任务失败");
        }
    }

    /**
     * 禁用文档定时任务。
     * <p>
     * 文档删除或关闭定时时只禁用 schedule 记录，保留历史运行状态和执行流水，方便后续排查。
     */
    private void disableDocumentSchedule(String docId) {
        scheduleMapper.update(null, new UpdateWrapper<KnowledgeDocumentScheduleDO>()
                .eq("doc_id", docId)
                .set("enabled", 0)
                .set("lock_owner", null)
                .set("lock_until", null));
    }

    private Date nextRunTime(String cronExpr, Date baseTime) {
        try {
            LocalDateTime base = LocalDateTime.ofInstant(baseTime.toInstant(), ZoneId.systemDefault());
            LocalDateTime next = CronExpression.parse(cronExpr.trim()).next(base);
            if (next == null) {
                throw new IllegalArgumentException("无法计算下一次执行时间");
            }
            return Date.from(next.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception ex) {
            throw new ClientException("定时表达式格式错误");
        }
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

    private String buildDocumentContentHash(MultipartFile file) {
        try {
            return KnowledgeContentHashUtils.sha256(file.getBytes());
        } catch (IOException ex) {
            throw new ServiceException("生成文档内容哈希失败：" + ex.getMessage());
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

    private record ChunkConfig(int chunkSize, int overlapSize) {
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
