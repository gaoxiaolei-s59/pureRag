package org.puregxl.site.rag.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.rag.config.RAGDefaultProperties;
import org.puregxl.site.rag.core.rewrite.QueryRewriteService;
import org.puregxl.site.rag.core.rewrite.RewriteResult;
import org.puregxl.site.rag.retrieval.RagRetrievalService;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.embedding.EmbeddingService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.framework.convention.RetrievedChunk;
import org.puregxl.site.infra.rerank.RerankService;
import org.puregxl.site.rag.service.MemoryService;
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
    private static final int REWRITE_HISTORY_TURNS = 4;

    private final EmbeddingService embeddingService;
    private final RagRetrievalService retrievalService;
    private final LLMService llmService;
    private final RAGDefaultProperties ragDefaultProperties;
    private final MemoryService memoryService;
    private final QueryRewriteService queryRewriteService;

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

        //1.加载对话记忆，后续与知识库召回内容一起组装到模型请求中。
        List<ChatMessage> memoryMessages = loadMemory(context);

        // 2.基于最近 4 轮用户/助手对话做问题改写，避免把摘要系统消息一并喂给改写模型。
        Rewrite(context.getQuestion(), latestRewriteHistory(memoryMessages), context);

        String question = context.getQuestion().trim();
        String retrievalQuestion = resolveRetrievalQuestion(context, question);
        List<Float> queryEmbedding = embeddingService.embed(retrievalQuestion);
        List<RetrievedChunk> candidates = retrievalService.searchSimilarChunks(
                ragDefaultProperties.getCollectionName(),
                queryEmbedding,
                safePositive(ragDefaultProperties.getRetrieveTopK(), 8));
//        List<RetrievedChunk> contextChunks = rerank(question, candidates);
        ChatRequest request = buildChatRequest(question, candidates, memoryMessages, context.isDeepThinking());

        log.info("[RAG问答] 开始流式生成，taskId={}, conversationId={}, retrieveCount={}, contextCount={}",
                context.getTaskId(), context.getConversationId(), candidates.size(), candidates.size());
        return llmService.streamChat(request, context.getCallback());
    }

    private List<ChatMessage> loadMemory(StreamChatContext context) {
        List<ChatMessage> chatMessages = memoryService.loadMemory(
                context.getUserId(),
                context.getConversationId(),
                ChatMessage.user(context.getQuestion())
        );
        context.setHistory(chatMessages);
        return chatMessages;
    }


    private ChatRequest buildChatRequest(String question, List<RetrievedChunk> contextChunks, List<ChatMessage> memoryMessages, boolean deepThinking) {
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(contextChunks)));
        if (CollUtil.isNotEmpty(memoryMessages)) {
            messages.addAll(memoryMessages);
        }
        messages.add(ChatMessage.user(question));
        return ChatRequest.builder()
                .messages(messages)
                .thinking(deepThinking)
                .temperature(DEFAULT_TEMPERATURE)
                .topP(DEFAULT_TOP_P)
                .build();
    }

    /**
     * 实现用户的问题改写
     */
    public void Rewrite(String userQuestion, List<ChatMessage> history, StreamChatContext context) {
        RewriteResult rewrite = queryRewriteService.rewrite(userQuestion, history);
        context.setRewriteResult(rewrite);
    }

    /**
     * 抽取最近的几轮对话
     * @param history
     * @return
     */
    private List<ChatMessage> latestRewriteHistory(List<ChatMessage> history) {
        if (CollUtil.isEmpty(history)) {
            return List.of();
        }
        List<ChatMessage> conversationalHistory = history.stream()
                .filter(message -> message != null && message.getRole() != ChatMessage.Role.SYSTEM)
                .toList();
        int keepMessages = REWRITE_HISTORY_TURNS * 2;
        if (conversationalHistory.size() <= keepMessages) {
            return conversationalHistory;
        }
        return conversationalHistory.subList(conversationalHistory.size() - keepMessages, conversationalHistory.size());
    }

    private String resolveRetrievalQuestion(StreamChatContext context, String originalQuestion) {
        RewriteResult rewriteResult = context.getRewriteResult();
        if (rewriteResult == null || StrUtil.isBlank(rewriteResult.getRewrittenQuestion())) {
            return originalQuestion;
        }
        return rewriteResult.getRewrittenQuestion().trim();
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
