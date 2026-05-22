package org.puregxl.site.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentScheduleDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentScheduleMapper;
import org.puregxl.site.knowledge.enums.DocumentStatus;
import org.puregxl.site.knowledge.enums.SourceType;
import org.puregxl.site.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.knowledge.service.KnowledgeDocumentScheduleService;
import org.puregxl.site.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.knowledge.util.KnowledgeContentHashUtils;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.exception.ServiceException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeDocumentScheduleServiceImpl implements KnowledgeDocumentScheduleService {

    private static final int DISPATCH_LIMIT = 50;
    private static final long LOCK_EXPIRE_MINUTES = 5L;
    private static final String LOCK_KEY_PREFIX = "knowledge:document-schedule:";

    private final KnowledgeDocumentScheduleMapper scheduleMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentService documentService;
    private final KnowledgeStorageResourceService storageResourceService;
    private final RedissonClient redissonClient;

    /**
     * 扫描并派发到期任务。
     * <p>
     * 这里不直接执行 Tika/Embedding/Milvus 这些重操作，只负责把到期任务转换成现有的文档分块事务消息。
     * 状态边界是：本方法只保证“已成功派发 MQ 事务消息”，真正的分块成功或失败由消费者里的
     * {@link KnowledgeDocumentService#executeChunk(String)} 负责更新文档状态。
     */
    @Override
    public void dispatchDueSchedules() {
        Date now = new Date();
        List<KnowledgeDocumentScheduleDO> schedules = scheduleMapper.selectList(new QueryWrapper<KnowledgeDocumentScheduleDO>()
                .eq("enabled", 1)
                .le("next_run_time", now)
                .orderByAsc("next_run_time")
                .last("limit " + DISPATCH_LIMIT));
        for (KnowledgeDocumentScheduleDO schedule : schedules) {
            dispatchOneSchedule(schedule);
        }
    }

    /**
     * 单条任务派发流程。
     * <p>
     * 先通过 Redisson 分布式锁抢占任务，抢锁成功后再校验文档是否仍然启用定时，最后复用文档分块入口
     * 发送 RocketMQ 事务消息。无论成功或失败，都会推进 nextRunTime，避免错误任务在同一分钟内被高频重试。
     */
    private void dispatchOneSchedule(KnowledgeDocumentScheduleDO schedule) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + schedule.getId());
        boolean locked;
        try {
            locked = lock.tryLock(0, LOCK_EXPIRE_MINUTES, TimeUnit.MINUTES);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[文档定时任务] 获取 Redisson 锁被中断，scheduleId={}", schedule.getId(), ex);
            return;
        }
        if (!locked) {
            log.info("[文档定时任务] 任务已被其它实例抢占，scheduleId={}", schedule.getId());
            return;
        }
        Date now = new Date();
        try {
            KnowledgeDocumentDO document = documentMapper.selectById(schedule.getDocId());
            if (document == null || !Integer.valueOf(1).equals(document.getEnabled())
                    || !Integer.valueOf(1).equals(document.getScheduleEnabled())) {
                disableSchedule(schedule, "文档不存在或已关闭定时任务");
                return;
            }
            Date nextRunTime = nextRunTime(schedule.getCronExpr(), now);
            if (refreshUrlDocumentIfUnchanged(schedule, document, now, nextRunTime)) {
                return;
            }
            documentService.startChunkKnowledgeDocument(schedule.getDocId());
            releaseSchedule(schedule.getId(), KnowledgeDocumentScheduleDO.builder()
                    .lastRunTime(now)
                    .lastStatus(DocumentStatus.RUNNING.getCode())
                    .lastError(null)
                    .nextRunTime(nextRunTime)
                    .build());
        } catch (Exception ex) {
            log.error("[文档定时任务] 派发失败，scheduleId={}, docId={}", schedule.getId(), schedule.getDocId(), ex);
            releaseSchedule(schedule.getId(), KnowledgeDocumentScheduleDO.builder()
                    .lastRunTime(now)
                    .lastStatus(DocumentStatus.FAILED.getCode())
                    .lastError(ex.getMessage())
                    .nextRunTime(safeNextRunTime(schedule.getCronExpr(), now))
                    .build());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * URL 来源文档的定时拉取去重流程。
     * <p>
     * 定时任务真正触发分块前，先重新下载源 URL 文件并计算文件级 SHA-256；如果和文档表记录的
     * contentHash 一致，说明源文件没有变化，本轮只推进 nextRunTime，不发送 MQ，也不会重建 MySQL Chunk
     * 和向量库数据。如果内容发生变化，则先覆盖 RustFS 文件并更新文档表文件元信息，再继续派发分块任务。
     *
     * @return true 表示本轮内容未变化，已经完成跳过处理；false 表示内容有变化或非 URL 来源，需要继续执行分块。
     */
    private boolean refreshUrlDocumentIfUnchanged(KnowledgeDocumentScheduleDO schedule,
                                                  KnowledgeDocumentDO document,
                                                  Date now,
                                                  Date nextRunTime) {
        if (!SourceType.URL.getCode().equalsIgnoreCase(document.getSourceType())) {
            return false;
        }
        if (StrUtil.isBlank(document.getSourceLocation())) {
            throw new ClientException("URL 来源文档缺少来源地址");
        }

        MultipartFile latestFile = downloadUrlFile(document.getSourceLocation().trim());
        String latestContentHash = buildDocumentContentHash(latestFile);
        if (DocumentStatus.SUCCESS.getCode().equals(document.getStatus())
                && StrUtil.isNotBlank(document.getContentHash())
                && document.getContentHash().equals(latestContentHash)) {
            log.info("[文档定时任务] URL 文档内容未变化，跳过分块，scheduleId={}, docId={}",
                    schedule.getId(), document.getId());
            releaseSchedule(schedule.getId(), KnowledgeDocumentScheduleDO.builder()
                    .lastRunTime(now)
                    .lastStatus(DocumentStatus.SUCCESS.getCode())
                    .lastError("文档内容未变化，跳过执行")
                    .nextRunTime(nextRunTime)
                    .build());
            return true;
        }

        KnowledgeBaseDO knowledgeBase = knowledgeBaseMapper.selectById(document.getKbId());
        if (knowledgeBase == null) {
            throw new ClientException("知识库不存在");
        }
        String objectKey = buildDocumentObjectKey(document.getId(), latestFile.getOriginalFilename());
        String fileUrl = storageResourceService.uploadDocument(knowledgeBase.getCollectionName(), objectKey, latestFile);
        KnowledgeDocumentDO update = KnowledgeDocumentDO.builder()
                .id(document.getId())
                .fileUrl(fileUrl)
                .contentHash(latestContentHash)
                .fileSize(latestFile.getSize())
                .fileType(resolveFileType(latestFile.getOriginalFilename()))
                .status(DocumentStatus.PENDING.getCode())
                .build();
        int updated = documentMapper.updateById(update);
        if (updated != 1) {
            throw new ServiceException("更新 URL 文档文件信息失败");
        }
        return false;
    }

    private void disableSchedule(KnowledgeDocumentScheduleDO schedule, String reason) {
        releaseSchedule(schedule.getId(), KnowledgeDocumentScheduleDO.builder()
                .enabled(0)
                .lastRunTime(new Date())
                .lastStatus(DocumentStatus.FAILED.getCode())
                .lastError(reason)
                .build());
    }

    /**
     * 释放任务锁并写回调度状态。
     * <p>
     * Redisson 锁只负责并发互斥，数据库这里只写调度业务状态：上次运行时间、上次状态、错误信息和下次执行时间。
     */
    private void releaseSchedule(String scheduleId, KnowledgeDocumentScheduleDO update) {
        UpdateWrapper<KnowledgeDocumentScheduleDO> wrapper = new UpdateWrapper<KnowledgeDocumentScheduleDO>()
                .eq("id", scheduleId);
        if (update.getEnabled() != null) {
            wrapper.set("enabled", update.getEnabled());
        }
        if (update.getLastRunTime() != null) {
            wrapper.set("last_run_time", update.getLastRunTime());
        }
        if (update.getLastStatus() != null) {
            wrapper.set("last_status", update.getLastStatus());
        }
        wrapper.set("last_error", update.getLastError());
        if (update.getNextRunTime() != null) {
            wrapper.set("next_run_time", update.getNextRunTime());
        }
        scheduleMapper.update(null, wrapper);
    }

    private Date safeNextRunTime(String cronExpr, Date baseTime) {
        try {
            return nextRunTime(cronExpr, baseTime);
        } catch (Exception ignored) {
            return new Date(baseTime.getTime() + 5 * 60 * 1000L);
        }
    }

    /**
     * 使用 Spring CronExpression 计算下一次执行时间。
     * <p>
     * cron 解析失败会抛给上层，由上层记录 lastError；这比静默跳过更容易定位用户填错表达式的问题。
     */
    private Date nextRunTime(String cronExpr, Date baseTime) {
        LocalDateTime base = LocalDateTime.ofInstant(baseTime.toInstant(), ZoneId.systemDefault());
        LocalDateTime next = CronExpression.parse(cronExpr).next(base);
        if (next == null) {
            throw new IllegalArgumentException("无法计算下一次执行时间");
        }
        return Date.from(next.atZone(ZoneId.systemDefault()).toInstant());
    }

    private MultipartFile downloadUrlFile(String sourceLocation) {
        try (InputStream inputStream = URI.create(sourceLocation).toURL().openStream()) {
            byte[] bytes = inputStream.readAllBytes();
            if (bytes.length == 0) {
                throw new ClientException("URL 文档内容为空");
            }
            String filename = resolveUrlFilename(sourceLocation);
            String contentType = StrUtil.blankToDefault(URLConnection.guessContentTypeFromName(filename),
                    "application/octet-stream");
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

    private String buildDocumentObjectKey(String docId, String docName) {
        String filename = docName.replace("\\", "_").replace("/", "_");
        return "docs/" + docId + "/" + filename;
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

    private String resolveFileType(String filename) {
        if (StrUtil.isBlank(filename) || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
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
            throw new UnsupportedOperationException("URL 文档定时拉取流程不需要 transferTo");
        }
    }
}
