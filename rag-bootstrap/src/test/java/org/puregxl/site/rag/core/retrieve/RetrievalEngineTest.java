package org.puregxl.site.rag.core.retrieve;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.NodeScoreFilters;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.channel.SearchChannelResult;
import org.puregxl.site.rag.enums.IntentKind;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalEngineTest {

    @Test
    void retrievalAggregatesParallelSubQuestionContextsIntoKbContext() {
        AtomicInteger taskCount = new AtomicInteger();
        Executor immediateExecutor = command -> {
            taskCount.incrementAndGet();
            command.run();
        };
        MultiChannelRetrievalEngine multiChannelRetrievalEngine = mock(MultiChannelRetrievalEngine.class);
        RetrievalEngine retrievalEngine = new RetrievalEngine(
                multiChannelRetrievalEngine,
                immediateExecutor,
                new NodeScoreFilters()
        );
        SubQuestionIntent first = new SubQuestionIntent("OA 怎么申请", List.of(
                NodeScore.builder()
                        .score(0.92D)
                        .intentNode(IntentNode.builder()
                                .id("intent-oa")
                                .name("OA 流程")
                                .kind(IntentKind.KB)
                                .kbId("kb-oa")
                                .collectionName("kb_collection_oa")
                                .build())
                        .build()
        ));
        SubQuestionIntent second = new SubQuestionIntent("报销制度是什么", List.of(
                NodeScore.builder()
                        .score(0.89D)
                        .intentNode(IntentNode.builder()
                                .id("intent-finance")
                                .name("财务制度")
                                .kind(IntentKind.KB)
                                .kbId("kb-finance")
                                .collectionName("kb_collection_finance")
                                .build())
                        .build()
        ));

        when(multiChannelRetrievalEngine.search(first, 4)).thenReturn(SearchChannelResult.builder()
                .retrievedChunks(List.of(
                        RetrievedChunk.builder().id("c1").text("OA 申请走审批流").score(0.91F).build()
                ))
                .intentChunks(Map.of(
                        "intent-oa", List.of(RetrievedChunk.builder().id("c1").text("OA 申请走审批流").score(0.91F).build())
                ))
                .build());
        when(multiChannelRetrievalEngine.search(second, 4)).thenReturn(SearchChannelResult.builder()
                .retrievedChunks(List.of(
                        RetrievedChunk.builder().id("c2").text("报销需要发票原件").score(0.88F).build()
                ))
                .intentChunks(Map.of(
                        "intent-finance", List.of(RetrievedChunk.builder().id("c2").text("报销需要发票原件").score(0.88F).build())
                ))
                .build());

        RetrievalContext result = retrievalEngine.retrieval(List.of(first, second), 4);

        assertThat(taskCount.get()).isEqualTo(2);
        assertThat(result.hasKb()).isTrue();
        assertThat(result.getKbContext()).contains("OA 怎么申请");
        assertThat(result.getKbContext()).contains("OA 申请走审批流");
        assertThat(result.getKbContext()).contains("报销制度是什么");
        assertThat(result.getKbContext()).contains("报销需要发票原件");
        assertThat(result.getIntentChunks()).containsKeys("intent-oa", "intent-finance");
        assertThat(result.getIntentChunks().get("intent-oa")).hasSize(1);
        assertThat(result.getMcpContext()).isEmpty();
    }

    @Test
    void retrievalFallsBackToEmptyContextWhenInputIsEmpty() {
        AtomicInteger taskCount = new AtomicInteger();
        Executor immediateExecutor = command -> {
            taskCount.incrementAndGet();
            command.run();
        };
        MultiChannelRetrievalEngine multiChannelRetrievalEngine = mock(MultiChannelRetrievalEngine.class);
        RetrievalEngine retrievalEngine = new RetrievalEngine(
                multiChannelRetrievalEngine,
                immediateExecutor,
                new NodeScoreFilters()
        );

        RetrievalContext result = retrievalEngine.retrieval(List.of(), 0);

        assertThat(taskCount.get()).isZero();
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getIntentChunks()).isEmpty();
    }

    @Test
    void retrievalBuildsKbContextFromGlobalChunksWhenNoIntentChunksExist() {
        Executor immediateExecutor = Runnable::run;
        MultiChannelRetrievalEngine multiChannelRetrievalEngine = mock(MultiChannelRetrievalEngine.class);
        RetrievalEngine retrievalEngine = new RetrievalEngine(
                multiChannelRetrievalEngine,
                immediateExecutor,
                new NodeScoreFilters()
        );
        SubQuestionIntent subIntent = new SubQuestionIntent("报销流程在哪申请", List.of(
                NodeScore.builder()
                        .score(0.35D)
                        .intentNode(IntentNode.builder()
                                .id("intent-finance")
                                .name("财务制度")
                                .kind(IntentKind.KB)
                                .kbId("kb-finance")
                                .collectionName("kb_collection_finance")
                                .build())
                        .build()
        ));
        when(multiChannelRetrievalEngine.search(subIntent, 5)).thenReturn(SearchChannelResult.builder()
                .retrievedChunks(List.of(
                        RetrievedChunk.builder().id("g1").text("全局检索命中：报销在 OA 发起").score(0.8F).build()
                ))
                .intentChunks(Map.of())
                .build());

        RetrievalContext result = retrievalEngine.retrieval(List.of(subIntent), 5);

        assertThat(result.getKbContext()).contains("全局向量检索结果");
        assertThat(result.getKbContext()).contains("报销在 OA 发起");
    }
}
