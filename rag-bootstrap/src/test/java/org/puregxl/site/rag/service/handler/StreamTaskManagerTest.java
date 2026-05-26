package org.puregxl.site.rag.service.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.puregxl.site.framework.web.SseEmitterSender;
import org.puregxl.site.infra.chat.StreamCancellationHandle;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamTaskManagerTest {

    private RedissonClient redissonClient;
    private RTopic topic;
    private RBucket<String> stateBucket;
    private RBucket<Boolean> cancelBucket;
    private StreamTaskManager manager;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        topic = mock(RTopic.class);
        stateBucket = mock(RBucket.class);
        cancelBucket = mock(RBucket.class);
        manager = new StreamTaskManager(redissonClient);

        when(redissonClient.getTopic("pureagent:stream")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any())).thenReturn(1);
        when(redissonClient.<Boolean>getBucket("pureagent:stream:cancel:task-1")).thenReturn(cancelBucket);
        when(redissonClient.<String>getBucket("pureagent:stream:state:task-1")).thenReturn(stateBucket);
        manager.subscribe();
    }

    @Test
    void registerCompletesImmediatelyWhenRedisAlreadyMarkedCancelled() {
        when(cancelBucket.get()).thenReturn(Boolean.TRUE);
        SseEmitterSender sender = mock(SseEmitterSender.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        manager.register("task-1", sender, () -> cancelled.set(true));

        assertThat(cancelled).isTrue();
        verify(sender).sendEvent("cancel", "task-1");
        verify(sender).sendEvent("done", "task-1");
        verify(sender).complete();
        verify(stateBucket).set("CANCELLED", StreamTaskManager.TASK_TTL);
    }

    @Test
    void bindHandleCancelsImmediatelyWhenTaskAlreadyCancelledLocally() {
        when(cancelBucket.get()).thenReturn(Boolean.TRUE);
        SseEmitterSender sender = mock(SseEmitterSender.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);

        manager.register("task-1", sender, () -> {
        });
        manager.bindHandle("task-1", handle);

        verify(handle).cancel();
    }

    @Test
    void cancelLocalDoesNotOverrideCompletedTask() {
        SseEmitterSender sender = mock(SseEmitterSender.class);

        manager.register("task-1", sender, () -> {
        });
        manager.markCompleted("task-1");
        manager.cancel("task-1");

        ArgumentCaptor<MessageListener<String>> captor = ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(String.class), captor.capture());
        captor.getValue().onMessage("pureagent:stream", "task-1");

        verify(sender, never()).sendEvent("cancel", "task-1");
        verify(sender, never()).complete();
        verify(stateBucket).set("COMPLETED", StreamTaskManager.TASK_TTL);
    }

    @Test
    void markCancelledIfRequestedUsesRedisFlagAsRuntimeFallback() {
        when(cancelBucket.get()).thenReturn(Boolean.FALSE, Boolean.TRUE);
        SseEmitterSender sender = mock(SseEmitterSender.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        manager.register("task-1", sender, () -> cancelled.set(true));
        manager.bindHandle("task-1", handle);

        assertThat(manager.markCancelledIfRequested("task-1")).isTrue();
        assertThat(cancelled).isTrue();
        verify(handle).cancel();
        verify(sender).sendEvent("cancel", "task-1");
        verify(sender).sendEvent("done", "task-1");
    }

    @Test
    void unregisterKeepsDistributedStateForLateObservers() {
        manager.unregister("task-1");

        verify(cancelBucket, never()).deleteAsync();
        verify(stateBucket, never()).deleteAsync();
    }
}
