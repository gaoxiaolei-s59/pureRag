package org.puregxl.site.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.rag.core.rewrite.RewriteResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.puregxl.site.rag.enums.IntentKind.SYSTEM;

/**
 * 意图识别的处理模块
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntentResolver {

    private final IntentClassifier intentClassifier;

    private final Executor intentRecognitionExecutor;

    private static final Double INTENT_MIN_VALUE = 0.5;

    private static final Integer MAX_INTENT_COUNT = 3;

    /**
     * 调用大模型实现子问题的打分
     *
     * @param rewriteResult
     * @return
     */
    public List<SubQuestionIntent> resolve(RewriteResult rewriteResult) {
        List<String> subQuestion = CollUtil.isNotEmpty(rewriteResult.getSubQuestions()) ?
                rewriteResult.getSubQuestions() : List.of(rewriteResult.getRewrittenQuestion());

        List<CompletableFuture<SubQuestionIntent>> tasks = subQuestion.stream()
                .map(q -> CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return new SubQuestionIntent(q, classifyIntents(q));
                            } catch (Exception e) {
                                log.error("子问题意图分类失败，降级为空意图，question：{}", q, e);
                                return new SubQuestionIntent(q, List.of());
                            }
                        },
                        intentRecognitionExecutor
                ))
                .toList();

        List<SubQuestionIntent> subIntents = tasks.stream()
                .map(CompletableFuture::join)
                .toList();

        return capTotalIntents(subIntents);
    }


    /**
     * 传入问题 - 返回打分记录 - 最多三条记录
     *
     * @param question
     * @return
     */
    public List<NodeScore> classifyIntents(String question) {
        List<NodeScore> scores = intentClassifier.classifiy(question);
        return new ArrayList<>(scores.stream()
                .filter(ns -> ns.getScore() >= INTENT_MIN_VALUE)
                .limit(MAX_INTENT_COUNT)
                .toList());
    }


    /**
     * 限制总意图数量不超过 MAX_INTENT_COUNT
     * <p>
     * 策略：
     * 1. 如果总数未超限，直接返回
     * 2. 如果超限，每个子问题至少保留 1 个最高分意图
     * 3. 剩余配额按分数从高到低分配给其他意图
     */
    public List<SubQuestionIntent> capTotalIntents(List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents)) {
            return List.of();
        }
        //按照分数从大到小排序
        subIntents.forEach(subIntent -> {
            if (CollUtil.isNotEmpty(subIntent.getNodeScores())) {
                subIntent.getNodeScores().sort(
                        Comparator.comparing(NodeScore::getScore).reversed()
                );
            }
        });

        int totalIntentCount = subIntents.stream()
                .mapToInt(item -> CollUtil.isEmpty(item.getNodeScores()) ? 0 : item.getNodeScores().size())
                .sum();
        if (totalIntentCount <= MAX_INTENT_COUNT) {
            return subIntents;
        }

        List<SubQuestionIntent> result = new ArrayList<>();
        List<IntentAllocation> remainingCandidates = new ArrayList<>();
        int guaranteedCount = 0;

        for (int questionIndex = 0; questionIndex < subIntents.size(); questionIndex++) {
            SubQuestionIntent current = subIntents.get(questionIndex);
            List<NodeScore> currentScores = CollUtil.isEmpty(current.getNodeScores()) ? new ArrayList<>() : new ArrayList<>(current.getNodeScores());
            if (currentScores.isEmpty()) {
                result.add(new SubQuestionIntent(current.getSubQuestion(), new ArrayList<>()));
                continue;
            }

            List<NodeScore> reserved = new ArrayList<>();
            reserved.add(currentScores.get(0));
            guaranteedCount++;
            result.add(new SubQuestionIntent(current.getSubQuestion(), reserved));

            for (int scoreIndex = 1; scoreIndex < currentScores.size(); scoreIndex++) {
                remainingCandidates.add(new IntentAllocation(questionIndex, currentScores.get(scoreIndex)));
            }
        }

        int remainingQuota = Math.max(0, MAX_INTENT_COUNT - guaranteedCount);
        remainingCandidates.stream()
                .sorted(Comparator.comparing((IntentAllocation allocation) -> allocation.nodeScore().getScore()).reversed())
                .limit(remainingQuota)
                .forEach(allocation -> result.get(allocation.subQuestionIndex()).getNodeScores().add(allocation.nodeScore()));

        return result;
    }


    /**
     * 判断是否为系统
     * @param nodeScores
     * @return
     */
    public boolean isSystemOnly(List<NodeScore> nodeScores) {
        return nodeScores.size() == 1
                && nodeScores.get(0).getIntentNode() != null
                && nodeScores.get(0).getIntentNode().getKind() == SYSTEM;
    }

    private record IntentAllocation(int subQuestionIndex, NodeScore nodeScore) {
    }

}
