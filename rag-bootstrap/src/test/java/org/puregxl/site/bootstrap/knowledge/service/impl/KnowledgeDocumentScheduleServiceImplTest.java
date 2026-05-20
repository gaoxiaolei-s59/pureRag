package org.puregxl.site.bootstrap.knowledge.service.impl;

import org.junit.jupiter.api.Test;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentScheduleDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentScheduleMapper;
import org.puregxl.site.bootstrap.knowledge.enums.DocumentStatus;
import org.puregxl.site.bootstrap.knowledge.enums.SourceType;
import org.puregxl.site.bootstrap.knowledge.service.KnowledgeDocumentService;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.bootstrap.knowledge.util.KnowledgeContentHashUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentScheduleServiceImplTest {

    @Test
    void dispatchDueSchedulesLocksTaskAndTriggersDocumentChunk() throws Exception {
        KnowledgeDocumentScheduleMapper scheduleMapper = mock(KnowledgeDocumentScheduleMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentService documentService = mock(KnowledgeDocumentService.class);
        KnowledgeStorageResourceService storageResourceService = mock(KnowledgeStorageResourceService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        KnowledgeDocumentScheduleServiceImpl service = new KnowledgeDocumentScheduleServiceImpl(
                scheduleMapper,
                documentMapper,
                knowledgeBaseMapper,
                documentService,
                storageResourceService,
                redissonClient);

        KnowledgeDocumentScheduleDO schedule = KnowledgeDocumentScheduleDO.builder()
                .id("schedule-1")
                .docId("doc-1")
                .kbId("kb-1")
                .cronExpr("0 0/5 * * * ?")
                .enabled(1)
                .nextRunTime(new Date(System.currentTimeMillis() - 1000))
                .build();
        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .enabled(1)
                .scheduleEnabled(1)
                .scheduleCron("0 0/5 * * * ?")
                .status(DocumentStatus.SUCCESS.getCode())
                .build();
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule));
        when(scheduleMapper.update(any(), any())).thenReturn(1);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        when(redissonClient.getLock("knowledge:document-schedule:schedule-1")).thenReturn(lock);
        when(lock.tryLock(0, 5, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        service.dispatchDueSchedules();

        verify(documentService).startChunkKnowledgeDocument("doc-1");
        verify(lock).unlock();
        @SuppressWarnings({"rawtypes", "unchecked"})
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(scheduleMapper).update(any(), wrapperCaptor.capture());
        String sqlSet = wrapperCaptor.getAllValues().stream()
                .filter(UpdateWrapper.class::isInstance)
                .map(UpdateWrapper.class::cast)
                .map(UpdateWrapper::getSqlSet)
                .collect(Collectors.joining("\n"));
        String values = wrapperCaptor.getAllValues().stream()
                .filter(UpdateWrapper.class::isInstance)
                .map(UpdateWrapper.class::cast)
                .map(wrapper -> wrapper.getParamNameValuePairs().values().toString())
                .collect(Collectors.joining("\n"));
        assertThat(sqlSet).contains("last_status");
        assertThat(values).contains(DocumentStatus.RUNNING.getCode());
        assertThat(sqlSet).contains("next_run_time");
    }

    @Test
    void dispatchDueSchedulesSkipsTaskWhenRedissonLockIsNotAcquired() throws Exception {
        KnowledgeDocumentScheduleMapper scheduleMapper = mock(KnowledgeDocumentScheduleMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentService documentService = mock(KnowledgeDocumentService.class);
        KnowledgeStorageResourceService storageResourceService = mock(KnowledgeStorageResourceService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        KnowledgeDocumentScheduleServiceImpl service = new KnowledgeDocumentScheduleServiceImpl(
                scheduleMapper,
                documentMapper,
                knowledgeBaseMapper,
                documentService,
                storageResourceService,
                redissonClient);

        KnowledgeDocumentScheduleDO schedule = KnowledgeDocumentScheduleDO.builder()
                .id("schedule-1")
                .docId("doc-1")
                .cronExpr("0 0/5 * * * ?")
                .enabled(1)
                .nextRunTime(new Date(System.currentTimeMillis() - 1000))
                .build();
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule));
        when(redissonClient.getLock("knowledge:document-schedule:schedule-1")).thenReturn(lock);
        when(lock.tryLock(0, 5, TimeUnit.MINUTES)).thenReturn(false);

        service.dispatchDueSchedules();

        verify(documentService, never()).startChunkKnowledgeDocument("doc-1");
        verify(lock, never()).unlock();
        verify(scheduleMapper, never()).update(any(), any());
    }

    @Test
    void dispatchDueSchedulesSkipsUrlDocumentWhenContentHashIsUnchanged() throws Exception {
        KnowledgeDocumentScheduleMapper scheduleMapper = mock(KnowledgeDocumentScheduleMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentService documentService = mock(KnowledgeDocumentService.class);
        KnowledgeStorageResourceService storageResourceService = mock(KnowledgeStorageResourceService.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        KnowledgeDocumentScheduleServiceImpl service = new KnowledgeDocumentScheduleServiceImpl(
                scheduleMapper,
                documentMapper,
                knowledgeBaseMapper,
                documentService,
                storageResourceService,
                redissonClient);

        Path tempFile = Files.createTempFile("rag-schedule-same-", ".txt");
        Files.writeString(tempFile, "same document");
        KnowledgeDocumentScheduleDO schedule = KnowledgeDocumentScheduleDO.builder()
                .id("schedule-1")
                .docId("doc-1")
                .kbId("kb-1")
                .cronExpr("0 0/5 * * * ?")
                .enabled(1)
                .nextRunTime(new Date(System.currentTimeMillis() - 1000))
                .build();
        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .sourceType(SourceType.URL.getCode())
                .sourceLocation(tempFile.toUri().toString())
                .contentHash(KnowledgeContentHashUtils.sha256("same document"))
                .enabled(1)
                .scheduleEnabled(1)
                .scheduleCron("0 0/5 * * * ?")
                .status(DocumentStatus.SUCCESS.getCode())
                .build();
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule));
        when(scheduleMapper.update(any(), any())).thenReturn(1);
        when(documentMapper.selectById("doc-1")).thenReturn(document);
        when(redissonClient.getLock("knowledge:document-schedule:schedule-1")).thenReturn(lock);
        when(lock.tryLock(0, 5, TimeUnit.MINUTES)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        service.dispatchDueSchedules();

        verify(documentService, never()).startChunkKnowledgeDocument("doc-1");
        verify(storageResourceService, never()).uploadDocument(any(), any(), any());
        verify(lock).unlock();
        @SuppressWarnings({"rawtypes", "unchecked"})
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(scheduleMapper).update(any(), wrapperCaptor.capture());
        String values = wrapperCaptor.getAllValues().stream()
                .filter(UpdateWrapper.class::isInstance)
                .map(UpdateWrapper.class::cast)
                .map(wrapper -> wrapper.getParamNameValuePairs().values().toString())
                .collect(Collectors.joining("\n"));
        assertThat(values).contains(DocumentStatus.SUCCESS.getCode());
        assertThat(values).contains("文档内容未变化，跳过执行");
    }
}
