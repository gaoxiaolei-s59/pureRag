package org.puregxl.site.rag.Idempotent;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.puregxl.site.framework.exception.ClientException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;


@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "idempotent-submit:%s";

    private final ExpressionParser expressionParser = new SpelExpressionParser();

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
        String key = resolveBusinessKey(joinPoint, idempotentSubmit.key());
        if (StrUtil.isBlank(key)) {
            throw new ClientException("幂等Key不能为空");
        }
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


    private String resolveBusinessKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        if (StrUtil.isBlank(keyExpression)) {
            return null;
        }
        Object value = parseExpression(joinPoint, keyExpression);
        return value == null ? null : String.valueOf(value);
    }


    private Object parseExpression(ProceedingJoinPoint joinPoint, String keyExpression) {
        if (!keyExpression.contains("#") && !keyExpression.contains("'")) {
            return keyExpression;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();

        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("args", args);
        context.setVariable("methodName", method.getName());
        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);
            if (parameterNames != null && i < parameterNames.length) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }
        return expressionParser.parseExpression(keyExpression).getValue(context);
    }

}
