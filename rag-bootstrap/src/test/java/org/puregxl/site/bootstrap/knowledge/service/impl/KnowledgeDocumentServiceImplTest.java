package org.puregxl.site.bootstrap.knowledge.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.bootstrap.knowledge.dao.entity.KnowledgeDocumentScheduleExecDO;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.bootstrap.knowledge.dao.mapper.KnowledgeDocumentScheduleExecMapper;
import org.puregxl.site.bootstrap.knowledge.enums.DocumentStatus;
import org.puregxl.site.bootstrap.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import org.puregxl.site.bootstrap.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.bootstrap.user.context.UserContext;
import org.puregxl.site.bootstrap.user.context.UserInfoDTO;
import org.puregxl.site.framework.mq.productor.MessageQueueProducer;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeDocumentServiceImplTest {

    @AfterEach
    void tearDown() {
        UserContext.removeUserContext();
    }

    @Test
    void startChunkKnowledgeDocumentSendsTransactionMessageAndLocalTransactionUpdatesStatusAndCreatesExecRecord() {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeStorageResourceService storageResourceService = mock(KnowledgeStorageResourceService.class);
        MessageQueueProducer messageQueueProducer = mock(MessageQueueProducer.class);
        KnowledgeDocumentScheduleExecMapper scheduleExecMapper = mock(KnowledgeDocumentScheduleExecMapper.class);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeBaseMapper,
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                storageResourceService,
                messageQueueProducer,
                scheduleExecMapper);

        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .docName("demo.pdf")
                .fileSize(123L)
                .processMode("chunk")
                .status(DocumentStatus.PENDING.getCode())
                .build();
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);
        when(scheduleExecMapper.insert(any(KnowledgeDocumentScheduleExecDO.class))).thenReturn(1);
        UserContext.setUserContext(UserInfoDTO.builder().userId("user-1").build());

        service.startChunkKnowledgeDocument("doc-1");

        @SuppressWarnings("unchecked")
        var transactionCaptor = org.mockito.ArgumentCaptor.forClass(Consumer.class);
        var eventCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeDocumentChunkEvent.class);
        verify(messageQueueProducer).sendInTransaction(
                eq("knowledge-document-chunk_topic"),
                eq("doc-1"),
                eq("文档分块任务"),
                eventCaptor.capture(),
                transactionCaptor.capture());
        assertThat(eventCaptor.getValue().getDocId()).isEqualTo("doc-1");
        assertThat(eventCaptor.getValue().getKbId()).isEqualTo("kb-1");
        assertThat(eventCaptor.getValue().getOperator()).isEqualTo("user-1");

        transactionCaptor.getValue().accept(null);

        var documentUpdateCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(documentUpdateCaptor.capture());
        assertThat(documentUpdateCaptor.getValue().getId()).isEqualTo("doc-1");
        assertThat(documentUpdateCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.RUNNING.getCode());

        var execCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeDocumentScheduleExecDO.class);
        verify(scheduleExecMapper).insert(execCaptor.capture());
        assertThat(execCaptor.getValue().getScheduleId()).isNull();
        assertThat(execCaptor.getValue().getDocId()).isEqualTo("doc-1");
        assertThat(execCaptor.getValue().getKbId()).isEqualTo("kb-1");
        assertThat(execCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.RUNNING.getCode());
        assertThat(execCaptor.getValue().getFileName()).isEqualTo("demo.pdf");
        assertThat(execCaptor.getValue().getFileSize()).isEqualTo(123L);
    }
}
