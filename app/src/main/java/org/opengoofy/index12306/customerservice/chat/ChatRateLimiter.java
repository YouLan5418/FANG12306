package org.opengoofy.index12306.customerservice.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis（Redisson）的按用户令牌桶限流器。
 * 防止单用户疯狂发消息导致 LLM 费用失控。
 * 速率通过 ai.chat.rate-limit-per-minute 配置（默认 10 条/分钟）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRateLimiter {

    private final RedissonClient redissonClient;
    private final ChatProperties properties;

    /**
     * 为指定用户获取一个令牌。超限时抛出异常。
     *
     * @param username 已认证用户
     * @throws RuntimeException 超出限流时抛出
     */
    public void acquire(String username) {
        String key = "chat:rate:" + username;
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // trySetRate 仅在未配置时设置，幂等
        limiter.trySetRate(
                RateType.OVERALL,
                properties.getRateLimitPerMinute(),
                1,
                RateIntervalUnit.MINUTES
        );
        if (!limiter.tryAcquire(1)) {
            log.warn("用户提问频率超限: {}", username);
            throw new RuntimeException("提问过于频繁，请稍后再试");
        }
    }
}