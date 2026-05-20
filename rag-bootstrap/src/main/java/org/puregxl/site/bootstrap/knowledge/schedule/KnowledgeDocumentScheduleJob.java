package org.puregxl.site.bootstrap.knowledge.schedule;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeDocumentScheduleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库文档定时任务入口。
 * <p>
 * 这个类只负责被 Spring 调度器唤醒，并把扫描逻辑委托给 service。真正的抢锁、状态更新和 MQ 派发都在
 * KnowledgeDocumentScheduleService 中完成，避免定时入口承载业务细节。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentScheduleJob {

    private final KnowledgeDocumentScheduleService scheduleService;

    /**
     * 固定频率扫描到期任务。
     * <p>
     * 使用 fixedDelay 而不是 fixedRate，避免上一轮扫描较慢时新一轮立即叠加；多实例场景下由 DB 锁保证
     * 同一个 schedule 只被一个实例派发。
     */
    @Scheduled(fixedDelayString = "${rag.knowledge.schedule.scan-delay:30000}")
    public void dispatchDueSchedules() {
        try {
            scheduleService.dispatchDueSchedules();
        } catch (Exception ex) {
            log.error("[文档定时任务] 扫描到期任务失败", ex);
        }
    }
}
