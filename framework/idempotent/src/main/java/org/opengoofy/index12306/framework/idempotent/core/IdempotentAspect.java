package org.opengoofy.index12306.framework.idempotent.core;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.opengoofy.index12306.framework.idempotent.annotation.Idempotent;
import org.opengoofy.index12306.framework.idempotent.enums.IdempotentSceneEnum;
import org.opengoofy.index12306.framework.idempotent.enums.IdempotentTypeEnum;
import org.opengoofy.index12306.framework.idempotent.toolkit.SpELUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 幂等切面。
 * RESTAPI 场景：基于 SpEL key + Redis SETNX，重复请求返回错误。
 * MQ 场景：基于唯一 key + Redis，重复消费抛 RepeatConsumptionException。
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String IDEMPOTENT_PREFIX = "idempotent:";
    private static final String RESTAPI_DEFAULT_PREFIX = "restapi:";
    private static final String MQ_DEFAULT_PREFIX = "mq:";

    @Around("@annotation(idempotent)")
    public Object idempotentInvocation(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        IdempotentSceneEnum scene = idempotent.scene();
        IdempotentTypeEnum type = idempotent.type();
        String uniqueKey = buildUniqueKey(joinPoint, idempotent);
        boolean acquired = acquireLock(uniqueKey, idempotent.keyTimeout());
        if (!acquired) {
            if (scene == IdempotentSceneEnum.MQ) {
                throw new RepeatConsumptionException(idempotent.message(), scene);
            }
            log.warn("幂等拦截: {}", uniqueKey);
            throw new RepeatConsumptionException(idempotent.message(), scene);
        }
        try {
            return joinPoint.proceed();
        } finally {
            if (scene == IdempotentSceneEnum.RESTAPI) {
                stringRedisTemplate.delete(uniqueKey);
            }
        }
    }

    private String buildUniqueKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();
        String prefix = StrUtil.isNotBlank(idempotent.uniqueKeyPrefix())
                ? idempotent.uniqueKeyPrefix()
                : (idempotent.scene() == IdempotentSceneEnum.MQ ? MQ_DEFAULT_PREFIX : RESTAPI_DEFAULT_PREFIX);
        Object keyObj = StrUtil.isNotBlank(idempotent.key())
                ? SpELUtil.parseKey(idempotent.key(), method, args)
                : generateDefaultKey(joinPoint, idempotent);
        return IDEMPOTENT_PREFIX + prefix + keyObj;
    }

    private String generateDefaultKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        if (idempotent.scene() == IdempotentSceneEnum.RESTAPI) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String token = request.getHeader("Authorization");
                String uri = request.getRequestURI();
                return uri + ":" + (token == null ? IdUtil.fastSimpleUUID() : token);
            }
        }
        return IdUtil.fastSimpleUUID();
    }

    private boolean acquireLock(String key, long timeout) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", timeout, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }
}
