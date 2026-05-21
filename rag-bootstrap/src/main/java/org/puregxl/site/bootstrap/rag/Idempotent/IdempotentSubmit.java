package org.puregxl.site.bootstrap.rag.Idempotent;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IdempotentSubmit {
    /**
     * 幂等业务 Key，支持 SpEL，例如：#message.uuid。
     * 为空时会尝试从第一个 MessageWrapper 参数中读取 uuid，uuid 为空再读取 keys。
     */
    String key() default "";

    /**
     * 重复提交或重复消费时的提示信息。
     */
    String message() default "请求正在处理中，请稍后再试";
}
