package org.puregxl.site.rag.Idempotent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.puregxl.site.framework.mq.MessageWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentConsumeAspectTest {

    private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
    private final IdempotentConsumeAspect aspect = new IdempotentConsumeAspect(stringRedisTemplate);

    @Test
    void proceedWhenRedisKeyCreated() throws Throwable {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        AtomicInteger proceedCount = new AtomicInteger();
        ProceedingJoinPoint joinPoint = joinPoint("consume", new Object[]{message("uuid-001")}, proceedCount);

        Object result = aspect.idempotentHandler(joinPoint, method("consume").getAnnotation(IdempotentConsume.class));

        assertThat(result).isEqualTo("ok");
        assertThat(proceedCount).hasValue(1);
    }

    @Test
    void skipBusinessWhenRedisKeyAlreadyExists() throws Throwable {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);
        AtomicInteger proceedCount = new AtomicInteger();
        ProceedingJoinPoint joinPoint = joinPoint("consume", new Object[]{message("uuid-001")}, proceedCount);

        Object result = aspect.idempotentHandler(joinPoint, method("consume").getAnnotation(IdempotentConsume.class));

        assertThat(result).isNull();
        assertThat(proceedCount).hasValue(0);
    }

    @Test
    void removeKeyWhenBusinessThrows() throws Throwable {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        ProceedingJoinPoint joinPoint = joinPoint("failedConsume", new Object[]{message("uuid-002")}, new AtomicInteger());

        try {
            aspect.idempotentHandler(joinPoint, method("failedConsume").getAnnotation(IdempotentConsume.class));
        } catch (IllegalStateException ignored) {
        }

        verify(stringRedisTemplate).delete("idempotent:consume:uuid-002");
    }

    @Test
    void useMessageUuidAsDefaultKey() throws Throwable {
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        ProceedingJoinPoint joinPoint = joinPoint("consumeByDefaultKey", new Object[]{message("uuid-003")}, new AtomicInteger());

        aspect.idempotentHandler(joinPoint, method("consumeByDefaultKey").getAnnotation(IdempotentConsume.class));

        verify(stringRedisTemplate).execute(any(RedisScript.class), anyList(), any(), any());
        verify(stringRedisTemplate, never()).delete("idempotent:consume:uuid-003");
    }

    @IdempotentConsume(key = "#message.uuid")
    String consume(MessageWrapper<String> message) {
        return "ok";
    }

    @IdempotentConsume(key = "#message.uuid")
    String failedConsume(MessageWrapper<String> message) {
        throw new IllegalStateException("failed");
    }

    @IdempotentConsume
    String consumeByDefaultKey(MessageWrapper<String> message) {
        return "ok";
    }

    private static MessageWrapper<String> message(String uuid) {
        return MessageWrapper.<String>builder()
                .uuid(uuid)
                .keys("biz-key")
                .body("body")
                .build();
    }

    private static Method method(String name) throws NoSuchMethodException {
        return IdempotentConsumeAspectTest.class.getDeclaredMethod(name, MessageWrapper.class);
    }

    private ProceedingJoinPoint joinPoint(String methodName, Object[] args, AtomicInteger proceedCount) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = method(methodName);

        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"message"});
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            proceedCount.incrementAndGet();
            try {
                return method.invoke(this, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        });
        return joinPoint;
    }
}
