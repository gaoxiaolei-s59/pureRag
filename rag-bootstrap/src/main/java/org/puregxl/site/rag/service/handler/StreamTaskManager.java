package org.puregxl.site.rag.service.handler;

import cn.hutool.core.util.StrUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.site.framework.web.SseEmitterSender;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.puregxl.site.rag.enums.TaskState;
import org.redisson.api.RedissonClient;
import org.redisson.api.RTopic;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreamTaskManager {
    private static final String CANCEL_TOPIC = "pureagent:stream";
    private static final String CANCEL_EVENT = "cancel";
    private static final String DONE_EVENT = "done";
    private static final String CANCEL_KEY_PREFIX = "pureagent:stream:cancel:";
    private static final String STATE_KEY_PREFIX = "pureagent:stream:state:";
    static final Duration TASK_TTL = Duration.ofMinutes(30);

    private final RedissonClient redissonClient;
    private final Cache<String, StreamTaskInfo> tasks = CacheBuilder.newBuilder()
            .expireAfterAccess(TASK_TTL.toMillis(), TimeUnit.MILLISECONDS)
            .maximumSize(10000)
            .build();
    private int listenerId = -1;

    @PostConstruct
    public void subscribe() {
        RTopic topic = redissonClient.getTopic(CANCEL_TOPIC);
        listenerId = topic.addListener(String.class, (channel, taskId) -> {
            if (StrUtil.isBlank(taskId)) {
                return;
            }
            cancelLocal(taskId);
        });
    }

    @PreDestroy
    public void unsubscribe() {
        if (listenerId == -1) {
            return;
        }
        redissonClient.getTopic(CANCEL_TOPIC).removeListener(listenerId);
    }

    /**
     * 注册本地流式任务上下文。
     * 这里会补查 Redis 取消标记，兜住“取消请求先到、任务稍后才完成注册”的竞态。
     */
    public void register(String taskId, SseEmitterSender sender, Runnable onCancelAction) {
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        taskInfo.sender = sender;
        taskInfo.onCancelAction = onCancelAction;
        if (isTaskCancelledInRedis(taskId, taskInfo)) {
            cancelLocal(taskId);
        }
    }

    /**
     * 绑定底层模型调用取消句柄。
     * 如果句柄绑定前任务已经被取消，这里要立刻补一次 cancel，避免底层连接继续输出。
     */
    public void bindHandle(String taskId, StreamCancellationHandle handle) {
        StreamTaskInfo taskInfo = getOrCreate(taskId);
        taskInfo.handle = handle;
        if (taskInfo.isTerminalCancelled() && handle != null) {
            handle.cancel();
        }
    }

    /**
     * 运行期兜底检查。
     * 调用方可在 onContent/onThinking/onComplete 前调用，确保即使广播偶发漏收，
     * 也能通过 Redis cancel 标记把任务停下来。
     *
     * @return true 表示任务已经取消，调用方应终止后续处理
     */
    public boolean markCancelledIfRequested(String taskId) {
        StreamTaskInfo taskInfo = tasks.getIfPresent(taskId);
        if (taskInfo == null) {
            return isRedisCancelled(taskId);
        }
        if (taskInfo.isTerminalCancelled()) {
            return true;
        }
        if (taskInfo.state.get() != TaskState.RUNNING) {
            return false;
        }
        if (!isTaskCancelledInRedis(taskId, taskInfo)) {
            return false;
        }
        cancelLocal(taskId);
        return true;
    }

    /**
     * 分布式发起取消。
     * 先写共享 cancel 标记，再广播 taskId，让在线节点能尽快停掉本地句柄。
     */
    public void cancel(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        redissonClient.getBucket(cancelKey(taskId)).set(Boolean.TRUE, TASK_TTL);
        redissonClient.getTopic(CANCEL_TOPIC).publish(taskId);
    }

    public boolean markCompleted(String taskId) {
        StreamTaskInfo taskInfo = tasks.getIfPresent(taskId);
        if (taskInfo == null) {
            redissonClient.<String>getBucket(stateKey(taskId)).set(TaskState.COMPLETED.name(), TASK_TTL);
            return true;
        }
        if (!taskInfo.state.compareAndSet(TaskState.RUNNING, TaskState.COMPLETED)) {
            return false;
        }
        redissonClient.<String>getBucket(stateKey(taskId)).set(TaskState.COMPLETED.name(), TASK_TTL);
        return true;
    }

    public boolean markFailed(String taskId) {
        StreamTaskInfo taskInfo = tasks.getIfPresent(taskId);
        if (taskInfo == null) {
            redissonClient.<String>getBucket(stateKey(taskId)).set(TaskState.FAILED.name(), TASK_TTL);
            return true;
        }
        if (!taskInfo.state.compareAndSet(TaskState.RUNNING, TaskState.FAILED)) {
            return false;
        }
        redissonClient.<String>getBucket(stateKey(taskId)).set(TaskState.FAILED.name(), TASK_TTL);
        return true;
    }

    /**
     * 只清理本地缓存，不删除 Redis 终态。
     * 让晚到观察者和重复取消请求在 TTL 窗口内还能看到最终状态。
     */
    public void unregister(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return;
        }
        tasks.invalidate(taskId);
    }

    private boolean isTaskCancelledInRedis(String taskId, StreamTaskInfo taskInfo) {
        if (taskInfo.isTerminalCancelled()) {
            return true;
        }
        if (taskInfo.state.get() != TaskState.RUNNING) {
            return false;
        }
        if (!isRedisCancelled(taskId)) {
            return false;
        }
        taskInfo.cancelRequested.set(true);
        return true;
    }

    private boolean isRedisCancelled(String taskId) {
        Boolean cancelled = redissonClient.<Boolean>getBucket(cancelKey(taskId)).get();
        return Boolean.TRUE.equals(cancelled);
    }

    /**
     * 本地执行取消收口。
     * 使用状态 CAS 保证取消、完成、失败三类终态只会有一个真正生效。
     */
    private void cancelLocal(String taskId) {
        StreamTaskInfo taskInfo = tasks.getIfPresent(taskId);
        if (taskInfo == null) {
            redissonClient.<String>getBucket(stateKey(taskId)).set(TaskState.CANCELLED.name(), TASK_TTL);
            return;
        }
        taskInfo.cancelRequested.set(true);
        if (!taskInfo.state.compareAndSet(TaskState.RUNNING, TaskState.CANCELLED)) {
            return;
        }

        StreamCancellationHandle handle = taskInfo.handle;
        if (handle != null) {
            handle.cancel();
        }
        if (taskInfo.onCancelAction != null) {
            taskInfo.onCancelAction.run();
        }
        if (taskInfo.sender != null) {
            taskInfo.sender.sendEvent(CANCEL_EVENT, taskId);
            taskInfo.sender.sendEvent(DONE_EVENT, taskId);
            taskInfo.sender.complete();
        }
        redissonClient.<String>getBucket(stateKey(taskId)).set(TaskState.CANCELLED.name(), TASK_TTL);
    }

    private StreamTaskInfo getOrCreate(String taskId) {
        try {
            return tasks.get(taskId, () -> new StreamTaskInfo(taskId));
        } catch (Exception ex) {
            throw new IllegalStateException("创建流式任务上下文失败", ex);
        }
    }

    private String cancelKey(String taskId) {
        return CANCEL_KEY_PREFIX + taskId;
    }

    private String stateKey(String taskId) {
        return STATE_KEY_PREFIX + taskId;
    }


    private static final class StreamTaskInfo {
        private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        private final AtomicReference<TaskState> state = new AtomicReference<>(TaskState.RUNNING);
        private volatile StreamCancellationHandle handle;
        private volatile SseEmitterSender sender;
        private volatile Runnable onCancelAction;

        private StreamTaskInfo(String taskId) {
        }

        private boolean isTerminalCancelled() {
            return cancelRequested.get() || state.get() == TaskState.CANCELLED;
        }
    }
}
