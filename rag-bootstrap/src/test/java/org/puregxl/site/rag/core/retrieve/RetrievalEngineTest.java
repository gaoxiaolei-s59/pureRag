package org.puregxl.site.rag.core.retrieve;

import org.junit.jupiter.api.Test;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.rag.core.intent.IntentNode;
import org.puregxl.site.rag.core.intent.NodeScoreFilters;
import org.puregxl.site.rag.core.intent.NodeScore;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.mcp.McpToolDispatcher;
import org.puregxl.site.rag.enums.IntentKind;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalEngineTest {

    @Test
    void retrievalBuildsKbContextFromFinalRerankedChunksWithStableFormat() {
        MultiChannelRetrievalEngine multiChannelRetrievalEngine = mock(MultiChannelRetrievalEngine.class);
        RetrievalEngine retrievalEngine = new RetrievalEngine(
                multiChannelRetrievalEngine,
                Runnable::run,
                new NodeScoreFilters(),
                mock(McpToolDispatcher.class)
        );
        SubQuestionIntent subIntent = new SubQuestionIntent("报销流程在哪申请", List.of());
        when(multiChannelRetrievalEngine.search(subIntent, 5)).thenReturn(List.of(
                RetrievedChunk.builder().id("r1").text("报销在 OA 系统发起").score(0.91F).build(),
                RetrievedChunk.builder().id("r2").text("提交时需要上传发票和审批单").score(0.86F).build()
        ));

        RetrievalContext result = retrievalEngine.retrieval(List.of(subIntent), 5, "user-1");

        assertThat(result.getKbContext()).isEqualTo("""
                子问题：报销流程在哪申请
                知识库检索结果：
                1. 报销在 OA 系统发起
                2. 提交时需要上传发票和审批单""");
        assertThat(result.getKbContext()).doesNotContain("全局向量检索结果");
        assertThat(result.getKbContext()).doesNotContain("知识意图");
        assertThat(result.getMcpContext()).isEmpty();
        assertThat(result.getIntentChunks()).isEmpty();
    }

    @Test
    void retrievalReturnsEmptyContextWhenFinalChunksAreEmpty() {
        MultiChannelRetrievalEngine multiChannelRetrievalEngine = mock(MultiChannelRetrievalEngine.class);
        RetrievalEngine retrievalEngine = new RetrievalEngine(
                multiChannelRetrievalEngine,
                Runnable::run,
                new NodeScoreFilters(),
                mock(McpToolDispatcher.class)
        );
        SubQuestionIntent subIntent = new SubQuestionIntent("没有命中的问题", List.of());
        when(multiChannelRetrievalEngine.search(subIntent, 3)).thenReturn(List.of());

        RetrievalContext result = retrievalEngine.retrieval(List.of(subIntent), 3, "user-1");

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.getIntentChunks()).isEmpty();
    }

    @Test
    void retrievalCallsMcpDispatcherByIntentToolIdAndMergesToolResults() {
        MultiChannelRetrievalEngine multiChannelRetrievalEngine = mock(MultiChannelRetrievalEngine.class);
        McpToolDispatcher mcpToolDispatcher = mock(McpToolDispatcher.class);
        RetrievalEngine retrievalEngine = new RetrievalEngine(
                multiChannelRetrievalEngine,
                Runnable::run,
                new NodeScoreFilters(),
                mcpToolDispatcher
        );
        IntentNode intentNode = IntentNode.builder()
                .id("exam-defer-mcp")
                .kind(IntentKind.MCP)
                .mcpToolId("rag_answer")
                .topK(2)
                .build();
        SubQuestionIntent subIntent = new SubQuestionIntent("缓考申请需要什么材料", List.of(
                NodeScore.builder()
                        .score(0.93D)
                        .intentNode(intentNode)
                        .build()
        ));
        when(multiChannelRetrievalEngine.search(subIntent, 5)).thenReturn(List.of());
        when(mcpToolDispatcher.call(intentNode, "缓考申请需要什么材料", 2, "user-1"))
                .thenReturn("{\"answer\":\"缓考申请需要证明材料\"}");

        RetrievalContext result = retrievalEngine.retrieval(List.of(subIntent), 5, "user-1");

        assertThat(result.getMcpContext()).isEqualTo("""
                子问题：缓考申请需要什么材料
                MCP 工具调用结果：
                1. 工具：rag_answer
                结果：{"answer":"缓考申请需要证明材料"}""");
        assertThat(result.hasMcp()).isTrue();
        assertThat(result.getKbContext()).isEmpty();
        verify(mcpToolDispatcher).call(intentNode, "缓考申请需要什么材料", 2, "user-1");
    }
}
