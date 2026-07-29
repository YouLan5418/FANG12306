package org.opengoofy.index12306.user.config;

import org.opengoofy.index12306.common.constant.UserConstant;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户注册防穿透布隆过滤器（用户名）。
 */
@Configuration
public class UserRegisterBloomConfig {

    @Bean
    public RBloomFilter<String> userRegisterCachePenetrationBloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> bloomFilter = redissonClient.getBloomFilter(UserConstant.USER_REGISTER_BLOOM_FILTER);
        bloomFilter.tryInit(1_000_000L, 0.01);
        return bloomFilter;
    }
}