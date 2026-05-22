package org.puregxl.site.rag.Idempotent;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentAspectTest {

    private final RedissonClient redissonClient = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final IdempotentAspect aspect = new IdempotentAspect(redissonClient);

    @Test
    void parseSubmitKeyByMethodArguments() throws Throwable {
        when(redissonClient.getLock("idempotent-submit:rag-chat:hello:conv-001:false")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        ProceedingJoinPoint joinPoint = joinPoint(
                "chat",
                new Object[]{"hello", "conv-001", false}
        );

        Object result = aspect.idempotentHandler(joinPoint, method("chat").getAnnotation(IdempotentSubmit.class));

        assertThat(result).isEqualTo("ok");
        verify(redissonClient).getLock("idempotent-submit:rag-chat:hello:conv-001:false");
        verify(lock).unlock();
    }

    @IdempotentSubmit(key = "'rag-chat:' + #userQuestion + ':' + #conversationId + ':' + #deepThinking")
    String chat(String userQuestion, String conversationId, Boolean deepThinking) {
        return "ok";
    }

    private static Method method(String name) throws NoSuchMethodException {
        return IdempotentAspectTest.class.getDeclaredMethod(name, String.class, String.class, Boolean.class);
    }

    private ProceedingJoinPoint joinPoint(String methodName, Object[] args) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = method(methodName);

        when(joinPoint.getArgs()).thenReturn(args);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"userQuestion", "conversationId", "deepThinking"});
        when(joinPoint.proceed()).thenReturn(method.invoke(this, args));
        return joinPoint;
    }
}
