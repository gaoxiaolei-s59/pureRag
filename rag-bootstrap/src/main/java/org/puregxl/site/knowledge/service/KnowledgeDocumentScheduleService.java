package org.puregxl.site.knowledge.service;

public interface KnowledgeDocumentScheduleService {

    /**
     * 扫描到期的文档定时任务，并触发文档分块流程。
     */
    void dispatchDueSchedules();
}
