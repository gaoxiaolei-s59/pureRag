package org.puregxl.site.rag.service.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.puregxl.site.knowledge.dao.entity.KnowledgeBaseDO;
import org.puregxl.site.knowledge.dao.mapper.KnowledgeBaseMapper;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.IntentClassifier;
import org.puregxl.site.rag.core.intent.IntentTreeCacheManager;
import org.puregxl.site.rag.dao.entity.IntentNodeDO;
import org.puregxl.site.rag.dao.mapper.IntentTreeMapper;
import org.puregxl.site.rag.dto.req.IntentNodeCreateRequest;
import org.puregxl.site.rag.dto.req.IntentNodeUpdateRequest;
import org.puregxl.site.rag.dto.resp.IntentNodeResponse;
import org.puregxl.site.rag.enums.IntentKind;
import org.puregxl.site.rag.enums.IntentLevel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentTreeServiceImplTest {

    @Test
    void queryIntentNodeMapsClassifierNodesToResponses() {
        IntentClassifier intentClassifier = mock(IntentClassifier.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        IntentTreeMapper intentTreeMapper = mock(IntentTreeMapper.class);
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeServiceImpl service = new IntentTreeServiceImpl(intentClassifier, knowledgeBaseMapper, intentTreeMapper, cacheManager);

        when(intentClassifier.queryIntentNodes()).thenReturn(List.of(
                IntentNode.builder()
                        .recordId("db-1")
                        .id("logistics-overseas")
                        .name("跨境物流")
                        .level(IntentLevel.CATEGORY)
                        .build(),
                IntentNode.builder()
                        .recordId("db-2")
                        .id("logistics-overseas-customs")
                        .kbId("1997857139737882625")
                        .name("清关流程")
                        .level(IntentLevel.TOPIC)
                        .parentId("logistics-overseas")
                        .kind(IntentKind.KB)
                        .description("跨境物流的清关申报、关税计算、禁运品规则等相关说明")
                        .examples(List.of("海淘包裹清关一般要多久？"))
                        .collectionName("kb_1997857139737882625")
                        .build()
        ));

        List<IntentNodeResponse> result = service.queryIntentNode();

        assertThat(result).hasSize(2);
        assertThat(result.get(1).getRecordId()).isEqualTo("db-2");
        assertThat(result.get(1).getId()).isEqualTo("logistics-overseas-customs");
        assertThat(result.get(1).getParentId()).isEqualTo("logistics-overseas");
        assertThat(result.get(1).getLevel()).isEqualTo(IntentLevel.TOPIC);
    }

    @Test
    void getIntentNodeByIdMapsDatabaseRecordToResponse() {
        IntentClassifier intentClassifier = mock(IntentClassifier.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        IntentTreeMapper intentTreeMapper = mock(IntentTreeMapper.class);
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeServiceImpl service = new IntentTreeServiceImpl(intentClassifier, knowledgeBaseMapper, intentTreeMapper, cacheManager);
        when(intentTreeMapper.selectById("db-3")).thenReturn(IntentNodeDO.builder()
                .id("db-3")
                .kbId("kb-3")
                .intentCode("intent-c")
                .name("意图C")
                .level(2)
                .parentCode("parent-c")
                .description("desc-c")
                .examples("[\"示例C\"]")
                .collectionName("kb_collection_c")
                .kind(0)
                .enabled(1)
                .build());

        IntentNodeResponse response = service.getIntentNodeById("db-3");

        assertThat(response.getRecordId()).isEqualTo("db-3");
        assertThat(response.getId()).isEqualTo("intent-c");
        assertThat(response.getExamples()).containsExactly("示例C");
        assertThat(response.getCollectionName()).isEqualTo("kb_collection_c");
    }

    @Test
    void createIntentNodeInsertsRecordAndClearsCache() {
        IntentClassifier intentClassifier = mock(IntentClassifier.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        IntentTreeMapper intentTreeMapper = mock(IntentTreeMapper.class);
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeServiceImpl service = new IntentTreeServiceImpl(intentClassifier, knowledgeBaseMapper, intentTreeMapper, cacheManager);
        IntentNodeCreateRequest request = IntentNodeCreateRequest.builder()
                .kbId("kb-1")
                .intentCode("intent-a")
                .name("意图A")
                .level(2)
                .parentCode("parent-a")
                .description("desc")
                .examples(List.of("示例1", "示例2"))
                .collectionName("kb_collection_a")
                .kind(0)
                .enabled(1)
                .build();
        when(knowledgeBaseMapper.selectById("kb-1")).thenReturn(KnowledgeBaseDO.builder()
                .id("kb-1")
                .collectionName("kb_collection_from_kb")
                .build());

        service.createIntentNode(request);

        ArgumentCaptor<IntentNodeDO> captor = ArgumentCaptor.forClass(IntentNodeDO.class);
        verify(intentTreeMapper).insert(captor.capture());
        assertThat(captor.getValue().getCollectionName()).isEqualTo("kb_collection_from_kb");
        verify(cacheManager).clear();
    }

    @Test
    void updateIntentNodeUpdatesRecordByIdAndClearsCache() {
        IntentClassifier intentClassifier = mock(IntentClassifier.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        IntentTreeMapper intentTreeMapper = mock(IntentTreeMapper.class);
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeServiceImpl service = new IntentTreeServiceImpl(intentClassifier, knowledgeBaseMapper, intentTreeMapper, cacheManager);
        when(intentTreeMapper.selectById("id-1")).thenReturn(IntentNodeDO.builder().id("id-1").intentCode("old").build());
        when(knowledgeBaseMapper.selectById("kb-2")).thenReturn(KnowledgeBaseDO.builder()
                .id("kb-2")
                .collectionName("kb_collection_updated")
                .build());
        IntentNodeUpdateRequest request = IntentNodeUpdateRequest.builder()
                .kbId("kb-2")
                .intentCode("intent-b")
                .name("意图B")
                .level(1)
                .parentCode("parent-b")
                .description("desc-b")
                .examples(List.of("示例B"))
                .collectionName("kb_collection_b")
                .kind(2)
                .enabled(1)
                .build();

        service.updateIntentNode("id-1", request);

        ArgumentCaptor<IntentNodeDO> captor = ArgumentCaptor.forClass(IntentNodeDO.class);
        verify(intentTreeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getCollectionName()).isEqualTo("kb_collection_updated");
        verify(cacheManager).clear();
    }

    @Test
    void deleteIntentNodeDeletesByIdAndClearsCache() {
        IntentClassifier intentClassifier = mock(IntentClassifier.class);
        KnowledgeBaseMapper knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        IntentTreeMapper intentTreeMapper = mock(IntentTreeMapper.class);
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeServiceImpl service = new IntentTreeServiceImpl(intentClassifier, knowledgeBaseMapper, intentTreeMapper, cacheManager);
        when(intentTreeMapper.selectById("id-2")).thenReturn(IntentNodeDO.builder().id("id-2").intentCode("intent-c").build());

        service.deleteIntentNode("id-2");

        verify(intentTreeMapper).deleteById("id-2");
        verify(cacheManager).clear();
    }
}
