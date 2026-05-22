package org.puregxl.site.knowledge.service.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeChunkDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentScheduleExecDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentScheduleExecMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentScheduleMapper;
import org.puregxl.site.knowledge.enums.DocumentStatus;
import org.puregxl.site.knowledge.mq.event.KnowledgeDocumentChunkEvent;
import org.puregxl.site.knowledge.service.resource.FileParseService;
import org.puregxl.site.knowledge.service.resource.KnowledgeStorageResourceService;
import org.puregxl.site.knowledge.service.resource.KnowledgeVectorResourceService;
import org.puregxl.site.user.context.UserContext;
import org.puregxl.site.user.context.UserInfoDTO;
import org.puregxl.site.framework.mq.productor.MessageQueueProducer;
import org.puregxl.site.infra.embedding.EmbeddingService;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        FileParseService fileParseService = mock(FileParseService.class);
        KnowledgeVectorResourceService vectorResourceService = mock(KnowledgeVectorResourceService.class);
        MessageQueueProducer messageQueueProducer = mock(MessageQueueProducer.class);
        KnowledgeDocumentScheduleExecMapper scheduleExecMapper = mock(KnowledgeDocumentScheduleExecMapper.class);
        KnowledgeDocumentScheduleMapper scheduleMapper = mock(KnowledgeDocumentScheduleMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeBaseMapper,
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                storageResourceService,
                fileParseService,
                vectorResourceService,
                messageQueueProducer,
                scheduleExecMapper,
                scheduleMapper,
                embeddingService);

        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .docName("demo.pdf")
                .fileSize(123L)
                .contentHash("doc-hash")
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
        assertThat(execCaptor.getValue().getScheduleId()).isEqualTo("manual");
        assertThat(execCaptor.getValue().getDocId()).isEqualTo("doc-1");
        assertThat(execCaptor.getValue().getKbId()).isEqualTo("kb-1");
        assertThat(execCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.RUNNING.getCode());
        assertThat(execCaptor.getValue().getFileName()).isEqualTo("demo.pdf");
        assertThat(execCaptor.getValue().getFileSize()).isEqualTo(123L);
        assertThat(execCaptor.getValue().getContentHash()).isEqualTo("doc-hash");
    }

    @Test
    void executeChunkParsesDocumentByTikaServiceAndStoresFixedSizeChunks() throws Exception {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeStorageResourceService storageResourceService = mock(KnowledgeStorageResourceService.class);
        FileParseService fileParseService = mock(FileParseService.class);
        KnowledgeVectorResourceService vectorResourceService = mock(KnowledgeVectorResourceService.class);
        MessageQueueProducer messageQueueProducer = mock(MessageQueueProducer.class);
        KnowledgeDocumentScheduleExecMapper scheduleExecMapper = mock(KnowledgeDocumentScheduleExecMapper.class);
        KnowledgeDocumentScheduleMapper scheduleMapper = mock(KnowledgeDocumentScheduleMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeBaseMapper,
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                storageResourceService,
                fileParseService,
                vectorResourceService,
                messageQueueProducer,
                scheduleExecMapper,
                scheduleMapper,
                embeddingService);

        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .docName("demo.pdf")
                .fileUrl("rustfs://test/docs/doc-1/demo.pdf")
                .chunkConfig("{\"chunkSize\":10,\"overlapSize\":2}")
                .status(DocumentStatus.RUNNING.getCode())
                .build();
        KnowledgeBaseDO knowledgeBase = KnowledgeBaseDO.builder()
                .id("kb-1")
                .embeddingModel("embedding-model")
                .collectionName("kb_collection")
                .build();
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(knowledgeBase);
        when(fileParseService.parseFileByTika("rustfs://test/docs/doc-1/demo.pdf")).thenReturn("0123456789abcdefghij");
        when(knowledgeChunkMapper.insert(any(KnowledgeChunkDO.class))).thenReturn(1);
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);
        when(embeddingService.embedBatch(List.of("0123456789", "89abcdefgh", "ghij"), "embedding-model"))
                .thenReturn(List.of(
                        List.of(0.1F, 0.2F),
                        List.of(0.3F, 0.4F),
                        List.of(0.5F, 0.6F)));
        UserContext.setUserContext(UserInfoDTO.builder().userId("user-1").build());

        service.executeChunk("doc-1");

        verify(fileParseService).parseFileByTika("rustfs://test/docs/doc-1/demo.pdf");
        verify(vectorResourceService).deleteDocumentChunks("kb_collection", "doc-1");
        var chunkCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeChunkDO.class);
        verify(knowledgeChunkMapper, times(3)).insert(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues())
                .extracting(KnowledgeChunkDO::getContent)
                .containsExactly("0123456789", "89abcdefgh", "ghij");
        assertThat(chunkCaptor.getAllValues())
                .extracting(KnowledgeChunkDO::getChunkIndex)
                .containsExactly(0, 1, 2);
        assertThat(chunkCaptor.getAllValues().get(0).getContentHash())
                .isEqualTo("84d89877f0d4041efb6bf91a16f0248f2fd573e6af05c19f96bedb9f882f7882");
        var vectorCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorResourceService).insertChunks(eq("kb_collection"), vectorCaptor.capture());
        assertThat(vectorCaptor.getValue()).hasSize(3);

        var documentUpdateCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(documentUpdateCaptor.capture());
        assertThat(documentUpdateCaptor.getValue().getId()).isEqualTo("doc-1");
        assertThat(documentUpdateCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.SUCCESS.getCode());
        assertThat(documentUpdateCaptor.getValue().getChunkCount()).isEqualTo(3);
    }

    @Test
    void executeChunkSkipsDuplicateContentHashWhenGeneratedChunksRepeat() throws Exception {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeStorageResourceService storageResourceService = mock(KnowledgeStorageResourceService.class);
        FileParseService fileParseService = mock(FileParseService.class);
        KnowledgeVectorResourceService vectorResourceService = mock(KnowledgeVectorResourceService.class);
        MessageQueueProducer messageQueueProducer = mock(MessageQueueProducer.class);
        KnowledgeDocumentScheduleExecMapper scheduleExecMapper = mock(KnowledgeDocumentScheduleExecMapper.class);
        KnowledgeDocumentScheduleMapper scheduleMapper = mock(KnowledgeDocumentScheduleMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeBaseMapper,
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                storageResourceService,
                fileParseService,
                vectorResourceService,
                messageQueueProducer,
                scheduleExecMapper,
                scheduleMapper,
                embeddingService);

        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .fileUrl("rustfs://test/docs/doc-1/demo.txt")
                .chunkConfig("{\"chunkSize\":2,\"overlapSize\":0}")
                .build();
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(KnowledgeBaseDO.builder()
                .id("kb-1")
                .embeddingModel("embedding-model")
                .collectionName("kb_collection")
                .build());
        when(fileParseService.parseFileByTika("rustfs://test/docs/doc-1/demo.txt")).thenReturn("aaaa");
        when(embeddingService.embedBatch(List.of("aa", "aa"), "embedding-model"))
                .thenReturn(List.of(List.of(0.1F), List.of(0.1F)));
        when(knowledgeChunkMapper.selectCount(any())).thenReturn(0L, 1L);
        when(knowledgeChunkMapper.insert(any(KnowledgeChunkDO.class))).thenReturn(1);
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);

        service.executeChunk("doc-1");

        verify(knowledgeChunkMapper, times(1)).insert(any(KnowledgeChunkDO.class));
        var vectorCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorResourceService).insertChunks(eq("kb_collection"), vectorCaptor.capture());
        assertThat(vectorCaptor.getValue()).hasSize(1);
        var documentUpdateCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(documentUpdateCaptor.capture());
        assertThat(documentUpdateCaptor.getValue().getChunkCount()).isEqualTo(1);
    }

    @Test
    void executeChunkDoesNotMarkSuccessWhenVectorInsertFails() throws Exception {
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeDocumentMapper knowledgeDocumentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper knowledgeChunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeStorageResourceService storageResourceService = mock(KnowledgeStorageResourceService.class);
        FileParseService fileParseService = mock(FileParseService.class);
        KnowledgeVectorResourceService vectorResourceService = mock(KnowledgeVectorResourceService.class);
        MessageQueueProducer messageQueueProducer = mock(MessageQueueProducer.class);
        KnowledgeDocumentScheduleExecMapper scheduleExecMapper = mock(KnowledgeDocumentScheduleExecMapper.class);
        KnowledgeDocumentScheduleMapper scheduleMapper = mock(KnowledgeDocumentScheduleMapper.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        KnowledgeDocumentServiceImpl service = new KnowledgeDocumentServiceImpl(
                knowledgeBaseMapper,
                knowledgeDocumentMapper,
                knowledgeChunkMapper,
                storageResourceService,
                fileParseService,
                vectorResourceService,
                messageQueueProducer,
                scheduleExecMapper,
                scheduleMapper,
                embeddingService);

        KnowledgeDocumentDO document = KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .fileUrl("rustfs://test/docs/doc-1/demo.pdf")
                .chunkConfig("{\"chunkSize\":10,\"overlapSize\":0}")
                .build();
        when(knowledgeDocumentMapper.selectById("doc-1")).thenReturn(document);
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(KnowledgeBaseDO.builder()
                .id("kb-1")
                .embeddingModel("embedding-model")
                .collectionName("kb_collection")
                .build());
        when(fileParseService.parseFileByTika("rustfs://test/docs/doc-1/demo.pdf")).thenReturn("0123456789");
        when(embeddingService.embedBatch(List.of("0123456789"), "embedding-model")).thenReturn(List.of(List.of(0.1F)));
        when(knowledgeChunkMapper.insert(any(KnowledgeChunkDO.class))).thenReturn(1);
        when(knowledgeDocumentMapper.updateById(any(KnowledgeDocumentDO.class))).thenReturn(1);
        org.mockito.Mockito.doThrow(new org.puregxl.site.framework.exception.ServiceException("写入向量失败"))
                .when(vectorResourceService).insertChunks(eq("kb_collection"), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.executeChunk("doc-1"))
                .isInstanceOf(org.puregxl.site.framework.exception.ServiceException.class);

        var updateCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeDocumentDO.class);
        verify(knowledgeDocumentMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo(DocumentStatus.FAILED.getCode());
        verify(vectorResourceService, never()).insertChunks(eq("missing"), any());
    }
}
