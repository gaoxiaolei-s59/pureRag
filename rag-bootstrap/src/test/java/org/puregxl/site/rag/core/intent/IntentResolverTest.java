package org.puregxl.site.rag.core.intent;

import org.junit.jupiter.api.Test;
import org.puregxl.site.rag.core.rewrite.RewriteResult;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class IntentResolverTest {

    private final IntentClassifier intentClassifier = mock(IntentClassifier.class);
    private final Executor directExecutor = Runnable::run;

    @Test
    void capTotalIntentsKeepsAtLeastOneIntentPerSubQuestionThenFillsHighestScores() {
        IntentResolver resolver = new IntentResolver(intentClassifier, directExecutor);
        List<SubQuestionIntent> result = resolver.capTotalIntents(List.of(
                new SubQuestionIntent("Q1", List.of(
                        NodeScore.builder().score(0.99D).intentNode(IntentNode.builder().id("A").build()).build(),
                        NodeScore.builder().score(0.93D).intentNode(IntentNode.builder().id("B").build()).build()
                )),
                new SubQuestionIntent("Q2", List.of(
                        NodeScore.builder().score(0.96D).intentNode(IntentNode.builder().id("C").build()).build(),
                        NodeScore.builder().score(0.70D).intentNode(IntentNode.builder().id("D").build()).build()
                ))
        ));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNodeScores()).extracting(score -> score.getIntentNode().getId())
                .containsExactly("A", "B");
        assertThat(result.get(1).getNodeScores()).extracting(score -> score.getIntentNode().getId())
                .containsExactly("C");
    }

    @Test
    void resolveUsesRewrittenQuestionWhenNoSubQuestionsProvided() {
        IntentResolver resolver = new IntentResolver(new IntentClassifier() {
            @Override
            public List<IntentNode> queryIntentNodes() {
                return List.of();
            }

            @Override
            public List<NodeScore> classifiy(String question) {
                return List.of(NodeScore.builder().score(0.8D).intentNode(IntentNode.builder().id(question).build()).build());
            }
        }, directExecutor);

        List<SubQuestionIntent> result = resolver.resolve(RewriteResult.builder()
                .rewrittenQuestion("海淘包裹清关一般要多久？")
                .subQuestions(List.of())
                .build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSubQuestion()).isEqualTo("海淘包裹清关一般要多久？");
        assertThat(result.get(0).getNodeScores()).hasSize(1);
    }
}
