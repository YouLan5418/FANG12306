package org.opengoofy.index12306.user.service;

import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.user.common.constant.RedisKeyConstant;
import org.opengoofy.index12306.user.toolkit.UserReuseUtil;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户名可用性判断：布隆过滤器 + 注销复用集合双重判断。
 * 独立成组件以断开"注册服务 -> 责任链 -> 注册服务"的循环依赖。
 */
@Component
@RequiredArgsConstructor
public class UserAvailabilityChecker {

    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * @return true 表示用户名可注册
     */
    public Boolean hasUsername(String username) {
        boolean contains = userRegisterCachePenetrationBloomFilter.contains(username);
        if (contains) {
            return stringRedisTemplate.opsForSet()
                    .isMember(RedisKeyConstant.USER_REGISTER_REUSE_SHARDING + UserReuseUtil.hashShardingIdx(username), username);
        }
        return true;
    }
}