package org.puregxl.site.knowledge.service.impl;

import org.junit.jupiter.api.Test;
import org.puregxl.site.knowledge.dao.entity.KnowledgeChunkDO;
import org.puregxl.site.knowledge.dao.entity.KnowledgeDocumentDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeChunkMapper;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeDocumentMapper;
import org.puregxl.site.knowledge.dto.request.KnowledgeChunkCreateRequest;
import org.puregxl.site.framework.exception.ClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeChunkServiceImplTest {

    @Test
    void createKnowledgeChunkStoresSha256ContentHash() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkServiceImpl service = new KnowledgeChunkServiceImpl(chunkMapper, documentMapper);

        when(documentMapper.selectById("doc-1")).thenReturn(KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .build());
        when(chunkMapper.selectCount(any())).thenReturn(0L);
        when(chunkMapper.insert(any(KnowledgeChunkDO.class))).thenReturn(1);

        KnowledgeChunkCreateRequest request = new KnowledgeChunkCreateRequest();
        request.setIndex(0);
        request.setContent("0123456789");

        service.createKnowledgeChunk("doc-1", request);

        var chunkCaptor = org.mockito.ArgumentCaptor.forClass(KnowledgeChunkDO.class);
        verify(chunkMapper).insert(chunkCaptor.capture());
        assertThat(chunkCaptor.getValue().getContentHash())
                .isEqualTo("84d89877f0d4041efb6bf91a16f0248f2fd573e6af05c19f96bedb9f882f7882");
    }

    @Test
    void createKnowledgeChunkRejectsDuplicateContentHashInSameDocument() {
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkServiceImpl service = new KnowledgeChunkServiceImpl(chunkMapper, documentMapper);

        when(documentMapper.selectById("doc-1")).thenReturn(KnowledgeDocumentDO.builder()
                .id("doc-1")
                .kbId("kb-1")
                .build());
        when(chunkMapper.selectCount(any())).thenReturn(1L);

        KnowledgeChunkCreateRequest request = new KnowledgeChunkCreateRequest();
        request.setIndex(0);
        request.setContent("0123456789");

        assertThatThrownBy(() -> service.createKnowledgeChunk("doc-1", request))
                .isInstanceOf(ClientException.class)
                .hasMessageContaining("Chunk 内容已存在");
        verify(chunkMapper, never()).insert(any(KnowledgeChunkDO.class));
    }
}
