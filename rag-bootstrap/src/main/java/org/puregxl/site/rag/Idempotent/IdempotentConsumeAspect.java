package org.puregxl.site.rag.Idempotent;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.puregxl.site.framework.errorcode.BaseErrorCode;
import org.puregxl.site.framework.exception.ClientException;
import org.puregxl.site.framework.mq.MessageWrapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Collections;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotentConsumeAspect {

    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:consume:";

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LUA_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 1 then
                return 0
            end
            redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """;

    private static final DefaultRedisScript<Long> IDEMPOTENT_SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * 基于 Redis Lua 的方法级幂等控制。
     * <p>
     * 进入业务方法前先用 Lua 原子判断并写入幂等 Key。写入成功说明当前线程拿到处理权，可以继续执行业务；
     * Key 已存在说明同一消息或同一请求已经在处理或处理完成，MQ 消费场景默认直接返回，避免重复消息再次执行业务。
     * 如果业务方法抛出异常，会删除本次抢占的 Key，让 RocketMQ 或调用方后续重试仍有机会重新处理。
     */
    @Around("@annotation(idempotentSubmit)")
    public Object idempotentHandler(ProceedingJoinPoint joinPoint, IdempotentConsume idempotentSubmit) throws Throwable {
        String idempotentKey = buildIdempotentKey(joinPoint, idempotentSubmit);
        Long executeResult = stringRedisTemplate.execute(
                IDEMPOTENT_SCRIPT,
                Collections.singletonList(idempotentKey),
                "1",
                String.valueOf(idempotentSubmit.expireSeconds())
        );

        if (!Long.valueOf(1L).equals(executeResult)) {
            log.info("[幂等校验] 重复请求或重复消费已跳过，key={}", idempotentKey);
            if (idempotentSubmit.throwOnRepeat()) {
                throw new ClientException(idempotentSubmit.message(), BaseErrorCode.IDEMPOTENT_TOKEN_DELETE_ERROR);
            }
            return null;
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            stringRedisTemplate.delete(idempotentKey);
            log.warn("[幂等校验] 业务执行失败，已释放幂等 Key，key={}", idempotentKey, throwable);
            throw throwable;
        }
    }

    /**
     * 构建最终 Redis Key。
     * <p>
     * 注解显式配置 key 时优先按 SpEL 解析；未配置时尝试从 MessageWrapper 中读取 uuid/keys。
     * 最终统一加上业务前缀，避免和 Redis 中其它业务 Key 发生冲突。
     */
    private String buildIdempotentKey(ProceedingJoinPoint joinPoint, IdempotentConsume idempotentSubmit) {
        String businessKey = resolveBusinessKey(joinPoint, idempotentSubmit.key());
        if (StrUtil.isBlank(businessKey)) {
            throw new ClientException("幂等Key不能为空", BaseErrorCode.IDEMPOTENT_TOKEN_NULL_ERROR);
        }
        return IDEMPOTENT_KEY_PREFIX + businessKey;
    }

    /**
     * 解析业务 Key。为了让 MQ 消费入口使用成本最低，默认支持 MessageWrapper.uuid；
     * 复杂场景可以通过 SpEL 显式指定参数字段，例如 #message.body.docId。
     */
    private String resolveBusinessKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        if (StrUtil.isNotBlank(keyExpression)) {
            Object value = parseExpression(joinPoint, keyExpression);
            return value == null ? null : String.valueOf(value);
        }

        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof MessageWrapper<?> messageWrapper) {
                return StrUtil.blankToDefault(messageWrapper.getUuid(), messageWrapper.getKeys());
            }
        }
        return null;
    }

    /**
     * 使用 Spring SpEL 解析注解中的 key 表达式。
     * <p>
     * 支持参数名、#p0/#a0、#args 等常见写法；如果传入的是普通字符串，则按字面量作为业务 Key。
     */
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
