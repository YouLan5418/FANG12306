package org.opengoofy.index12306.framework.cache.config;

import org.opengoofy.index12306.framework.cache.DistributedCache;
import org.opengoofy.index12306.framework.cache.StringRedisTemplateProxy;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
public class CacheAutoConfiguration {

    @Bean
    public DistributedCache distributedCache(StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient) {
        return new StringRedisTemplateProxy(stringRedisTemplate, redissonClient);
    }
}
