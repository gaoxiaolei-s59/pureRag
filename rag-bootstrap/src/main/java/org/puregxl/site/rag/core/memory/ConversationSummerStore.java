package org.puregxl.site.rag.core.memory;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.config.MemoryProperties;
import org.puregxl.site.infra.chat.LLMService;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.puregxl.site.infra.framework.convention.ChatRequest;
import org.puregxl.site.infra.util.LLMResponseCleaner;
import org.puregxl.site.rag.dao.entity.ConversationSummerDO;
import org.puregxl.site.rag.dao.entity.MemoryDO;
import org.puregxl.site.rag.dao.mapper.ConversationMapper;
import org.puregxl.site.rag.dao.mapper.ConversationSummerMapper;
import org.puregxl.site.rag.dao.mapper.MemoryMapper;
import org.puregxl.site.rag.support.PromptTemplateLoader;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Service
@Slf4j
public class ConversationSummerStore implements ConversationSummerService {
    private static final String PROMPT = "prompt/conversation-summary-prompt.txt";
    private static final int MAX_BATCH_MESSAGES = 64;
    private static final int HISTORY_BUDGET_CHARS = 24_000;
    private static final int MESSAGE_BUDGET_CHARS = 8_000;
    private static final String OMITTED = "…[原文中段已省略]…";

    private final ConversationSummerMapper summaryMapper;
    private final MemoryMapper memoryMapper;
    private final ConversationMapper conversationMapper;
    private final MemoryProperties properties;
    private final LLMService llmService;
    private final PromptTemplateLoader promptLoader;
    private final RedissonClient redisson;
    private final TransactionOperations transactions;
    private final Executor executor;

    public ConversationSummerStore(ConversationSummerMapper summaryMapper, MemoryMapper memoryMapper, ConversationMapper conversationMapper,
                                   MemoryProperties properties, LLMService llmService, PromptTemplateLoader promptLoader,
                                   RedissonClient redisson, TransactionOperations transactions,
                                   @Qualifier("memorySummaryExecutor") Executor executor) {
        this.summaryMapper = summaryMapper;
        this.memoryMapper = memoryMapper;
        this.conversationMapper = conversationMapper;
        this.properties = properties;
        this.llmService = llmService;
        this.promptLoader = promptLoader;
        this.redisson = redisson;
        this.transactions = transactions;
        this.executor = executor;
    }

    @Override
    public ChatMessage loadSummary(String conversationId, String userId) {
        if (StrUtil.hasBlank(conversationId, userId)) {
            return null;
        }
        ConversationSummerDO row = latestSummary(conversationId, userId);
        if (row == null || StrUtil.isBlank(row.getSummaryContent())) {
            return null;
        }
        return ChatMessage.system("以下是此前对话的摘要，仅作为历史背景。\n" + row.getSummaryContent());
    }

    /** 任务在问答提交后入队。队列满或模型失败时保留原文，下次对话可继续处理。 */
    @Override
    public void scheduleCompression(String conversationId, String userId) {
        if (!Boolean.TRUE.equals(properties.getSummaryEnabled()) || StrUtil.hasBlank(conversationId, userId)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    compress(conversationId, userId);
                } catch (RuntimeException ex) {
                    log.warn("刷新会话摘要失败, conversationId={}", conversationId, ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            log.warn("会话摘要队列已满，保留原文等待下次刷新, conversationId={}", conversationId);
        }
    }

    /**
     * 按用户和会话互斥刷新。达到触发轮数后让摘要覆盖原文窗口的前半段；
     * 只要窗口最早的用户消息仍被覆盖，就可复用已有摘要。
     */
    private void compress(String conversationId, String userId) {
        RLock lock = redisson.getLock("rag:summary:" + userId.length() + ":" + userId + ":" + conversationId);
        if (!lock.tryLock()) {
            return;
        }
        try {
            int keep = properties.getHistoryKeepTurns();
            int trigger = properties.resolveSummaryStartTurns();
            List<MemoryDO> recentTurns = memoryMapper.selectList(scope(conversationId, userId)
                    .select(MemoryDO::getId, MemoryDO::getCreateTime, MemoryDO::getCompressedFlag)
                    .eq(MemoryDO::getRole, "user")
                    .orderByDesc(MemoryDO::getCreateTime, MemoryDO::getId)
                    .last("limit " + trigger));
            if (recentTurns.size() < trigger || Integer.valueOf(1).equals(recentTurns.get(keep - 1).getCompressedFlag())) {
                return;
            }
            MemoryDO cutoff = recentTurns.get((keep - 1) / 2);
            ConversationSummerDO summary = latestSummary(conversationId, userId);
            // 旧会话升级后可能积压大量原文，逐批提交，失败时保留尚未提交的批次。
            List<MemoryDO> batch;
            do {
                batch = pendingBefore(conversationId, userId, cutoff);
                if (batch.isEmpty()) {
                    break;
                }
                String text = generateSummary(summary, batch);
                if (StrUtil.isBlank(text)) {
                    break;
                }
                summary = saveBatch(conversationId, userId, summary, batch, text);
            } while (batch.size() == MAX_BATCH_MESSAGES);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private LambdaQueryWrapper<MemoryDO> scope(String conversationId, String userId) {
        return Wrappers.lambdaQuery(MemoryDO.class)
                .eq(MemoryDO::getUserId, userId)
                .eq(MemoryDO::getConversationId, conversationId);
    }

    private List<MemoryDO> pendingBefore(String conversationId, String userId, MemoryDO cutoff) {
        LambdaQueryWrapper<MemoryDO> query = scope(conversationId, userId)
                .select(MemoryDO::getId, MemoryDO::getRole, MemoryDO::getContent)
                .in(MemoryDO::getRole, "user", "assistant")
                .and(flag -> flag.eq(MemoryDO::getCompressedFlag, 0).or().isNull(MemoryDO::getCompressedFlag));
        // MySQL 升序排序时 NULL 在最前；旧消息即使缺少时间戳，也按主键稳定参与压缩。
        if (cutoff.getCreateTime() == null) {
            query.isNull(MemoryDO::getCreateTime).lt(MemoryDO::getId, cutoff.getId());
        } else {
            query.and(time -> time.isNull(MemoryDO::getCreateTime).or().lt(MemoryDO::getCreateTime, cutoff.getCreateTime())
                    .or(exact -> exact.eq(MemoryDO::getCreateTime, cutoff.getCreateTime()).lt(MemoryDO::getId, cutoff.getId())));
        }
        return memoryMapper.selectList(query.orderByAsc(MemoryDO::getCreateTime, MemoryDO::getId)
                .last("limit " + MAX_BATCH_MESSAGES));
    }

    private ConversationSummerDO latestSummary(String conversationId, String userId) {
        return summaryMapper.selectOne(Wrappers.lambdaQuery(ConversationSummerDO.class)
                .eq(ConversationSummerDO::getUserId, userId)
                .eq(ConversationSummerDO::getConversationId, conversationId)
                .orderByDesc(ConversationSummerDO::getUpdateTime, ConversationSummerDO::getId)
                .last("limit 1"));
    }

    private String generateSummary(ConversationSummerDO existing, List<MemoryDO> batch) {
        StringBuilder history = new StringBuilder();
        // 每条都有独立额度；截长消息时标出省略部分并保留两端，原文始终留库。
        int perMessage = Math.min(MESSAGE_BUDGET_CHARS, HISTORY_BUDGET_CHARS / batch.size() - 16);
        for (MemoryDO row : batch) {
            if (StrUtil.isNotBlank(row.getContent())) {
                history.append(row.getRole()).append(": ").append(excerpt(row.getContent(), perMessage)).append('\n');
            }
        }
        String previous = existing == null ? "无" : StrUtil.blankToDefault(existing.getSummaryContent(), "无");
        previous = excerpt(previous, properties.getSummaryMaxChars());
        String instruction = promptLoader.load(PROMPT)
                .replace("{{summaryMaxChars}}", String.valueOf(properties.getSummaryMaxChars()));
        String data = "已有摘要：\n" + previous + "\n\n本次新增历史：\n" + history;
        String raw = llmService.chat(ChatRequest.builder()
                .messages(List.of(ChatMessage.system(instruction), ChatMessage.user(data)))
                .temperature(0.1D).topP(0.8D).build());
        String result = LLMResponseCleaner.stripMarkdownCodeFence(raw);
        if (StrUtil.isBlank(result)) {
            return null;
        }
        int length = Math.min(properties.getSummaryMaxChars(), result.codePointCount(0, result.length()));
        return result.substring(0, result.offsetByCodePoints(0, length));
    }

    /** 按 Unicode 码点分割，保留消息的开头和结尾。 */
    private String excerpt(String content, int limit) {
        if (content.codePointCount(0, content.length()) <= limit) {
            return content;
        }
        int available = limit - OMITTED.length();
        int head = content.offsetByCodePoints(0, available / 2);
        int tail = content.offsetByCodePoints(content.length(), -(available - available / 2));
        return content.substring(0, head) + OMITTED + content.substring(tail);
    }

    /**
     * 模型调用位于事务之外。在短事务中校验本批每条消息仍待压缩，再写入滚动摘要；
     * 即使锁过期、消息被删除或摘要写入失败，也不会单独推进覆盖标记。
     */
    private ConversationSummerDO saveBatch(String conversationId, String userId, ConversationSummerDO previous,
                                          List<MemoryDO> batch, String text) {
        return transactions.execute(status -> {
            // Redis 锁丢失后另一个进程可能已提交前一批。除校验本批标记外，还要确认模型读到的摘要仍是最新版。
            conversationMapper.lockOwnedConversation(conversationId, userId);
            ConversationSummerDO current = latestSummary(conversationId, userId);
            boolean unchanged = previous == null ? current == null : current != null
                    && Objects.equals(previous.getId(), current.getId())
                    && Objects.equals(previous.getSummaryContent(), current.getSummaryContent());
            if (!unchanged) {
                throw new IllegalStateException("摘要生成期间历史摘要已更新");
            }
            List<String> ids = batch.stream().map(MemoryDO::getId).toList();
            int changed = memoryMapper.update(Wrappers.lambdaUpdate(MemoryDO.class)
                    .eq(MemoryDO::getConversationId, conversationId).eq(MemoryDO::getUserId, userId)
                    .and(flag -> flag.eq(MemoryDO::getCompressedFlag, 0).or().isNull(MemoryDO::getCompressedFlag))
                    .in(MemoryDO::getId, ids).set(MemoryDO::getCompressedFlag, 1));
            if (changed != ids.size()) {
                throw new IllegalStateException("摘要覆盖范围已变化");
            }
            if (previous == null) {
                ConversationSummerDO row = ConversationSummerDO.builder().conversationId(conversationId)
                        .UserId(userId).SummaryContent(text).build();
                summaryMapper.insert(row);
                return row;
            }
            int updated = summaryMapper.update(Wrappers.lambdaUpdate(ConversationSummerDO.class)
                    .eq(ConversationSummerDO::getId, previous.getId())
                    .set(ConversationSummerDO::getSummaryContent, text));
            if (updated != 1) {
                throw new IllegalStateException("更新会话摘要失败");
            }
            return previous.setSummaryContent(text);
        });
    }
}
