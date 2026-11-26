package org.puregxl.site.rag.core.usermemory;

import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.infra.framework.convention.ChatMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** 将账户的长期事实作为历史参考提供给聊天入口，后台依序消费已提交的用户消息。 */
@Slf4j
@Service
public class UserMemoryService {
    private final UserMemoryQueue queue;
    private final UserMemoryExtractor extractor;
    private final Executor executor;
    private final Set<String> activeUsers = ConcurrentHashMap.newKeySet();

    public UserMemoryService(UserMemoryQueue queue, UserMemoryExtractor extractor,
                             @Qualifier("userMemoryExecutor") Executor executor) {
        this.queue = queue;
        this.extractor = extractor;
        this.executor = executor;
    }

    /** 保留用户角色，模型可参考历史偏好，当轮发言仍作为最后一条用户消息。 */
    public ChatMessage load(String userId) {
        if (!queue.enabled() || userId == null || userId.isBlank()) {
            return null;
        }
        try {
            List<String> facts = queue.facts(userId);
            if (!facts.isEmpty()) {
                return ChatMessage.user("历史用户资料（仅作背景参考，以当前问题为准）：\n- " + String.join("\n- ", facts));
            }
        } catch (RuntimeException ex) {
            log.warn("加载长期记忆失败，本轮沿用会话上下文，userId={}", userId, ex);
        }
        return null;
    }

    /** 正常聊天在整轮保存成功后唤醒学习。正文从同一事务持久化的源消息读取。 */
    public void learn(String userId) {
        if (queue.enabled() && userId != null && !userId.isBlank()) {
            schedule(userId);
        }
    }

    /** 宕机、任务队列已满和模型暂时不可用时，通过数据库队首恢复。 */
    @Scheduled(fixedDelayString = "${rag.memory.long-term-retry-ms:30000}")
    public void recover() {
        if (queue.enabled()) {
            queue.readyUsers().forEach(this::schedule);
        }
    }

    private void schedule(String userId) {
        if (!activeUsers.add(userId)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    drain(userId);
                } catch (RuntimeException ex) {
                    log.warn("长期记忆后台任务将重试，userId={}", userId, ex);
                } finally {
                    activeUsers.remove(userId);
                }
            });
        } catch (RejectedExecutionException ex) {
            activeUsers.remove(userId);
            log.debug("长期记忆线程繁忙，稍后从数据库恢复，userId={}", userId);
        }
    }

    /** 每轮限制批量，模型推理期间不占数据库事务。模型失败时保留原快照及待学任务。 */
    private void drain(String userId) {
        for (int count = 0; count < 8; count++) {
            UserMemoryQueue.Job job = queue.next(userId);
            if (job == null) {
                return;
            }
            List<String> next;
            try {
                next = job.content() == null ? job.facts() : extractor.extract(job.facts(), job.content());
            } catch (RuntimeException ex) {
                if (queue.defer(userId, job)) {
                    log.error("长期记忆学习任务连续失败，已终止，userId={}, jobId={}", userId, job.id(), ex);
                } else {
                    log.warn("长期记忆抽取失败，任务稍后重试，userId={}, jobId={}", userId, job.id(), ex);
                }
                return;
            }
            if (!queue.complete(userId, job, next)) {
                return;
            }
        }
    }
}
