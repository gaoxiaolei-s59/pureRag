package org.puregxl.site.rag.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.rag.config.SearchChannelProperties;
import org.puregxl.site.rag.core.intent.IntentResolver;
import org.puregxl.site.rag.core.intent.SubQuestionIntent;
import org.puregxl.site.rag.core.retrieve.RetrievalContext;
import org.puregxl.site.rag.core.retrieve.RetrievalEngine;
import org.puregxl.site.rag.core.rewrite.QueryRewriteService;
import org.puregxl.site.rag.core.rewrite.RewriteResult;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.rag.service.MemoryService;
import org.puregxl.site.rag.support.PromptTemplateLoader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPipeLine {
    private static final double DEFAULT_TEMPERATURE = 0.2D;
    private static final double DEFAULT_TOP_P = 0.8D;
    private static final int REWRITE_HISTORY_TURNS = 4;
    private static final String SYSTEM_PROMPT_RESOURCE_PATH = "prompt/rag-system-prompt.txt";

    private final LLMService llmService;
    private final MemoryService memoryService;
    private final QueryRewriteService queryRewriteService;
    private final PromptTemplateLoader promptTemplateLoader;
    private final IntentResolver intentResolver;
    private final RetrievalEngine retrievalEngine;
    private final SearchChannelProperties searchChannelProperties;

    /**
     * 执行一次基础 RAG 流式问答。
     * <p>
     * 主流程先加载记忆、改写问题并解析意图；如果本轮全部是 SYSTEM 意图，则直接调用模型。
     * 否则统一进入 RetrievalEngine，由它负责多通道检索、去重、rerank 和 MCP 上下文编排，
     * ChatPipeLine 只把最终上下文组装进系统 Prompt 后交给大模型流式输出。
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

        // 3.多路并行检索子问题
        resolveIntents(context);

        // 4.检查是否全部命中SYSTEM
        StreamCancellationHandle systemOnlyHandle = handleSystemOnly(context, memoryMessages);
        if (systemOnlyHandle != null) {
            return systemOnlyHandle;
        }

        // 5.非 SYSTEM 问题统一进入多通道检索链路，避免继续走旧的默认 Collection 单路检索。
        String question = context.getQuestion().trim();
        RetrievalContext retrievalContext = retrieval(context);
        ChatRequest request = buildChatRequest(question, retrievalContext, memoryMessages, context.isDeepThinking());

        log.info("[RAG问答] 开始流式生成，taskId={}, conversationId={}, hasKb={}, hasMcp={}",
                context.getTaskId(), context.getConversationId(),
                retrievalContext != null && retrievalContext.hasKb(),
                retrievalContext != null && retrievalContext.hasMcp());
        return llmService.streamChat(request, context.getCallback());
    }

    /**
     * 评测专用旁路：
     * 复用正式问答链路里“生成前”的全部步骤，只返回结构化快照，不触发大模型生成。
     */
    public PipelineEvalResult evaluatePipeline(String userQuestion,
                                               String conversationId,
                                               Boolean deepThinking,
                                               String userId) {
        if (StrUtil.isBlank(userQuestion)) {
            throw new ClientException("用户问题不能为空");
        }

        StreamChatContext context = StreamChatContext.builder()
                .question(userQuestion.trim())
                .conversationId(conversationId)
                .deepThinking(Boolean.TRUE.equals(deepThinking))
                .userId(userId)
                .callback(null)
                .build();

        long totalStart = System.nanoTime();

        List<ChatMessage> history = loadMemory(context);

        long rewriteStart = System.nanoTime();
        Rewrite(context.getQuestion(), latestRewriteHistory(history), context);
        long rewriteLatencyMs = elapsedMillis(rewriteStart);

        long intentStart = System.nanoTime();
        resolveIntents(context);
        long intentLatencyMs = elapsedMillis(intentStart);

        boolean allSystemOnly = isAllSystemOnly(context.getSubIntents());

        RetrievalContext retrievalContext = null;
        long retrievalLatencyMs = 0L;
        if (!allSystemOnly) {
            long retrievalStart = System.nanoTime();
            retrievalContext = retrieval(context);
            retrievalLatencyMs = elapsedMillis(retrievalStart);
        }

        return PipelineEvalResult.builder()
                .userQuestion(context.getQuestion())
                .conversationId(context.getConversationId())
                .deepThinking(context.isDeepThinking())
                .history(history)
                .rewriteResult(context.getRewriteResult())
                .subIntents(context.getSubIntents())
                .allSystemOnly(allSystemOnly)
                .retrievalContext(retrievalContext)
                .rewriteLatencyMs(rewriteLatencyMs)
                .intentLatencyMs(intentLatencyMs)
                .retrievalLatencyMs(retrievalLatencyMs)
                .totalLatencyMs(elapsedMillis(totalStart))
                .build();
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


    /**
     * 发起调用方法
     * @param context
     * @return
     */
    public RetrievalContext retrieval(StreamChatContext context) {
        // 这里统一把子问题意图和默认 TopK 交给检索引擎做通道拆分，
        // ChatPipeLine 只负责流程编排，不重复承担 KB/MCP 过滤职责。
        return retrievalEngine.retrieval(context.getSubIntents(), searchChannelProperties.getDefaultTopK(), context.getUserId());
    }


    /**
     * 构建带检索上下文的大模型请求。
     * <p>
     * RetrievalContext 中的 KB/MCP 内容已经是上游编排后的最终文本，
     * 这里不再重新理解来源，只负责按 Prompt 模板注入。
     */
    private ChatRequest buildChatRequest(String question,
                                         RetrievalContext retrievalContext,
                                         List<ChatMessage> memoryMessages,
                                         boolean deepThinking) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(retrievalContext)));
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
     * 构建不依赖知识库检索的直连模型请求。
     * <p>
     * 当所有子问题都被判定为 SYSTEM 意图时，说明本轮问题更像通用聊天/写作/解释，不需要走知识库召回。
     * 这时只保留对话记忆和当前用户问题，避免把“知识库未命中”的系统提示词误传给模型。
     */
    private ChatRequest buildDirectChatRequest(String question, List<ChatMessage> memoryMessages, boolean deepThinking) {
        List<ChatMessage> messages = new ArrayList<>();
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
     * 判断是否所有的子节点命中的意图都为System
     * @return
     */
    private StreamCancellationHandle handleSystemOnly(StreamChatContext context, List<ChatMessage> memoryMessages) {
        List<SubQuestionIntent> subIntents = context.getSubIntents();
        boolean allSystemOnly = isAllSystemOnly(subIntents);
        if (!allSystemOnly) {
            return null;
        }

        String question = context.getQuestion().trim();
        ChatRequest request = buildDirectChatRequest(question, memoryMessages, context.isDeepThinking());
        log.info("[RAG问答] 命中纯 SYSTEM 意图，跳过检索直连模型，taskId={}, conversationId={}, subQuestionCount={}",
                context.getTaskId(), context.getConversationId(), subIntents.size());
        return llmService.streamChat(request, context.getCallback());
    }

    private boolean isAllSystemOnly(List<SubQuestionIntent> subIntents) {
        if (CollUtil.isEmpty(subIntents)) {
            return false;
        }
        return subIntents.stream().allMatch(subIntent ->
                subIntent != null
                        && CollUtil.isNotEmpty(subIntent.getNodeScores())
                        && intentResolver.isSystemOnly(subIntent.getNodeScores()));
    }


    /**
     * 进行意图识别
     *
     * @param context
     */
    public void resolveIntents(StreamChatContext context) {
        List<SubQuestionIntent> subQuestionIntents = intentResolver.resolve(context.getRewriteResult());
        context.setSubIntents(subQuestionIntents);
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
     *
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

    private String buildSystemPrompt(RetrievalContext retrievalContext) {
        return promptTemplateLoader.load(SYSTEM_PROMPT_RESOURCE_PATH)
                .replace("{{context}}", buildRetrievalContextText(retrievalContext));
    }

    private String buildRetrievalContextText(RetrievalContext retrievalContext) {
        if (retrievalContext == null || retrievalContext.isEmpty()) {
            return "未检索到可用知识库片段。";
        }

        List<String> contexts = new ArrayList<>();
        if (retrievalContext.hasKb()) {
            contexts.add(retrievalContext.getKbContext().trim());
        }
        if (retrievalContext.hasMcp()) {
            contexts.add(retrievalContext.getMcpContext().trim());
        }
        return contexts.isEmpty() ? "未检索到可用知识库片段。" : String.join("\n\n", contexts);
    }

    private long elapsedMillis(long startNanoTime) {
        return Math.max(0L, (System.nanoTime() - startNanoTime) / 1_000_000L);
    }
}
