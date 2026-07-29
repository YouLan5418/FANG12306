package org.opengoofy.index12306.framework.idempotent.annotation;

import org.opengoofy.index12306.framework.idempotent.enums.IdempotentSceneEnum;
import org.opengoofy.index12306.framework.idempotent.enums.IdempotentTypeEnum;

import java.lang.annotation.*;

/**
 * 幂等注解。
 * 通过 AOP 拦截，结合 SpEL 解析 key，使用 Redis 完成防重。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 Key，支持 SpEL 表达式（如 #username）
     */
    String key() default "";

    /**
     * 触发幂等失败时的提示信息
     */
    String message() default "您操作太快，请稍后再试";

    /**
     * 幂等类型
     */
    IdempotentTypeEnum type() default IdempotentTypeEnum.PARAM;

    /**
     * 幂等场景：RESTAPI 或 MQ
     */
    IdempotentSceneEnum scene() default IdempotentSceneEnum.RESTAPI;

    /**
     * 防重 Key 前缀
     */
    String uniqueKeyPrefix() default "";

    /**
     * Key 过期时间，单位秒，默认 1 小时
     */
    long keyTimeout() default 3600L;
}
