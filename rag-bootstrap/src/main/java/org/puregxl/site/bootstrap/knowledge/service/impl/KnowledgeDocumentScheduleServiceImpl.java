package org.puregxl.site.bootstrap.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentScheduleDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentScheduleMapper;
import org.puregxl.site.bootstrap.knowledge.enums.DocumentStatus;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeDocumentScheduleService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
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
    private final KnowledgeDocumentService documentService;
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
}
