package org.opengoofy.index12306.framework.idempotent.config;

import org.opengoofy.index12306.framework.idempotent.core.IdempotentAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class IdempotentAutoConfiguration {

    @Bean
    public IdempotentAspect idempotentAspect(StringRedisTemplate stringRedisTemplate) {
        return new IdempotentAspect(stringRedisTemplate);
    }
}
