package org.puregxl.site.rag.core.intent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.rag.dao.entity.IntentNodeDO;
import org.puregxl.site.rag.dao.mapper.IntentTreeMapper;
import org.puregxl.site.rag.support.PromptTemplateLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultIntentClassifierTest {

    @Test
    void queryIntentNodesReturnsCachedNodesDirectly() {
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeMapper mapper = mock(IntentTreeMapper.class);
        LLMService llmService = mock(LLMService.class);
        DefaultIntentClassifier classifier = new DefaultIntentClassifier(cacheManager, mapper, llmService, new PromptTemplateLoader());

        when(cacheManager.getIntentCache()).thenReturn(List.of(
                IntentNode.builder()
                        .id("logistics-overseas")
                        .name("跨境物流")
                        .build()
        ));

        List<IntentNode> result = classifier.queryIntentNodes();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("logistics-overseas");
        verify(mapper, never()).selectList(any());
    }

    @Test
    void queryIntentNodesLoadsDatabaseBuildsTreeAndCaches() {
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeMapper mapper = mock(IntentTreeMapper.class);
        LLMService llmService = mock(LLMService.class);
        DefaultIntentClassifier classifier = new DefaultIntentClassifier(cacheManager, mapper, llmService, new PromptTemplateLoader());

        when(cacheManager.getIntentCache()).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(List.of(
                IntentNodeDO.builder()
                        .id("1")
                        .kbId("kb-parent")
                        .intentCode("logistics-overseas")
                        .name("跨境物流")
                        .level(1)
                        .description("跨境物流说明")
                        .kind(0)
                        .enabled(1)
                        .sortOrder(1)
                        .build(),
                IntentNodeDO.builder()
                        .id("2")
                        .kbId("1997857139737882625")
                        .intentCode("logistics-overseas-customs")
                        .parentCode("logistics-overseas")
                        .name("清关流程")
                        .level(2)
                        .description("跨境物流的清关申报、关税计算、禁运品规则等相关说明")
                        .examples("[\"海淘包裹清关一般要多久？\"]")
                        .collectionName("kb_1997857139737882625")
                        .kind(0)
                        .enabled(1)
                        .sortOrder(2)
                        .build()
        ));

        List<IntentNode> result = classifier.queryIntentNodes();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("logistics-overseas");
        assertThat(result.get(0).getChildren()).containsExactly("logistics-overseas-customs");
        assertThat(result.get(1).getFullPath()).isEqualTo("跨境物流 > 清关流程");
        assertThat(result.get(1).getExamples()).containsExactly("海淘包裹清关一般要多久？");

        ArgumentCaptor<List<IntentNode>> cacheCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheManager).setIntentCache(cacheCaptor.capture());
        assertThat(cacheCaptor.getValue()).hasSize(2);
    }

    @Test
    void classifiyUsesLeafNodesAndParsesModelJsonScores() {
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeMapper mapper = mock(IntentTreeMapper.class);
        LLMService llmService = mock(LLMService.class);
        DefaultIntentClassifier classifier = new DefaultIntentClassifier(cacheManager, mapper, llmService, new PromptTemplateLoader());

        when(cacheManager.getIntentCache()).thenReturn(List.of(
                IntentNode.builder()
                        .id("logistics-overseas")
                        .name("跨境物流")
                        .children(List.of("logistics-overseas-customs"))
                        .fullPath("跨境物流")
                        .build(),
                IntentNode.builder()
                        .id("logistics-overseas-customs")
                        .kbId("1997857139737882625")
                        .name("清关流程")
                        .description("跨境物流的清关申报、关税计算、禁运品规则等相关说明")
                        .examples(List.of("海淘包裹清关一般要多久？"))
                        .collectionName("kb_1997857139737882625")
                        .fullPath("跨境物流 > 清关流程")
                        .build()
        ));
        when(llmService.chat(anyString())).thenReturn("""
                {
                  "matches": [
                    {"id":"logistics-overseas-customs","score":0.91}
                  ]
                }
                """);

        List<NodeScore> result = classifier.classifiy("海淘包裹清关一般要多久？");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScore()).isEqualTo(0.91D);
        assertThat(result.get(0).getIntentNode().getId()).isEqualTo("logistics-overseas-customs");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("海淘包裹清关一般要多久？")
                .contains("logistics-overseas-customs")
                .doesNotContain("logistics-overseas\"");
    }

    @Test
    void classifiyFallsBackToEmptyWhenModelResponseCannotBeParsed() {
        IntentTreeCacheManager cacheManager = mock(IntentTreeCacheManager.class);
        IntentTreeMapper mapper = mock(IntentTreeMapper.class);
        LLMService llmService = mock(LLMService.class);
        DefaultIntentClassifier classifier = new DefaultIntentClassifier(cacheManager, mapper, llmService, new PromptTemplateLoader());

        when(cacheManager.getIntentCache()).thenReturn(List.of(
                IntentNode.builder()
                        .id("logistics-overseas-customs")
                        .name("清关流程")
                        .build()
        ));
        when(llmService.chat(anyString())).thenReturn("我觉得这像清关流程");

        assertThat(classifier.classifiy("海淘包裹清关一般要多久？")).isEmpty();
    }
}
