package org.puregxl.site.rag.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.rag.config.RAGDefaultProperties;
import org.puregxl.site.rag.retrieval.RagRetrievalService;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.infra.rerank.RerankService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPipeLine {
    private static final double DEFAULT_TEMPERATURE = 0.2D;
    private static final double DEFAULT_TOP_P = 0.8D;

    private final EmbeddingService embeddingService;
    private final RagRetrievalService retrievalService;
    private final RerankService rerankService;
    private final LLMService llmService;
    private final RAGDefaultProperties ragDefaultProperties;

    /**
     * 执行一次基础 RAG 流式问答。
     * <p>
     * 主流程分为四步：先对用户问题向量化，再到默认 Collection 召回候选 Chunk，随后用 rerank
     * 压缩成少量高相关上下文，最后把上下文和用户问题组装为 ChatRequest 交给大模型流式输出。
     */
    public StreamCancellationHandle execute(StreamChatContext context) {
        if (context == null || StrUtil.isBlank(context.getQuestion())) {
            throw new ClientException("用户问题不能为空");
        }
        if (context.getCallback() == null) {
            throw new ClientException("流式回调不能为空");
        }

        String question = context.getQuestion().trim();
        List<Float> queryEmbedding = embeddingService.embed(question);
        List<RetrievedChunk> candidates = retrievalService.searchSimilarChunks(
                ragDefaultProperties.getCollectionName(),
                queryEmbedding,
                safePositive(ragDefaultProperties.getRetrieveTopK(), 8));
//        List<RetrievedChunk> contextChunks = rerank(question, candidates);
        ChatRequest request = buildChatRequest(question, candidates, context.isDeepThinking());

        log.info("[RAG问答] 开始流式生成，taskId={}, conversationId={}, retrieveCount={}, contextCount={}",
                context.getTaskId(), context.getConversationId(), candidates.size(), candidates.size());
        return llmService.streamChat(request, context.getCallback());
    }

//    private List<RetrievedChunk> rerank(String question, List<RetrievedChunk> candidates) {
//        if (CollUtil.isEmpty(candidates)) {
//            return List.of();
//        }
//        int topN = safePositive(ragDefaultProperties.getRerankTopN(), 4);
//        return rerankService.rerank(question, candidates, topN);
//    }

    private ChatRequest buildChatRequest(String question, List<RetrievedChunk> contextChunks, boolean deepThinking) {
        return ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(buildSystemPrompt(contextChunks)),
                        ChatMessage.user(question)))
                .thinking(deepThinking)
                .temperature(DEFAULT_TEMPERATURE)
                .topP(DEFAULT_TOP_P)
                .build();
    }

    private String buildSystemPrompt(List<RetrievedChunk> contextChunks) {
        String contextText = CollUtil.isEmpty(contextChunks)
                ? "未检索到可用知识库片段。"
                : IntStream.range(0, contextChunks.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + StrUtil.blankToDefault(contextChunks.get(i).getText(), ""))
                .collect(Collectors.joining("\n\n"));

        return """
                你是一个严谨的知识库问答助手。请优先依据“知识库上下文”回答用户问题。
                如果上下文不足以回答，请明确说明知识库中没有足够信息，不要编造。
                回答应简洁、准确；使用到上下文时，可以在句末标注对应编号，例如 [1]。

                知识库上下文：
                %s
                """.formatted(contextText);
    }

    private int safePositive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
