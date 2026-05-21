package org.puregxl.site.bootstrap.rag.Idempotent;

import cn.hutool.crypto.digest.DigestUtil;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.puregxl.site.bootstrap.user.context.UserContext;
import org.puregxl.site.framework.exception.ClientException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;


@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final RedissonClient redissonClient;

    private final String LOCK_PREFIX = "idempotent-submit:%s";

    /**
     * 幂等控制
     * @param joinPoint
     * @param idempotentSubmit
     * @return
     * @throws Throwable
     */
    @Around("@annotation(idempotentSubmit)")
    public Object idempotentHandler(
            ProceedingJoinPoint joinPoint,
            IdempotentSubmit idempotentSubmit
    ) throws Throwable {
        String key = idempotentSubmit.key();
        RLock lock = redissonClient.getLock(String.format(LOCK_PREFIX, key));
        if (!lock.tryLock()) {
            throw new ClientException(idempotentSubmit.message());
        }
        Object result;
        try {
            result = joinPoint.proceed();
        } finally {
            lock.unlock();
        }
        return result;
    }

}
