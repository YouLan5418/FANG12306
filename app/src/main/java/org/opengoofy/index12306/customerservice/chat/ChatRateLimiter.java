package org.opengoofy.index12306.customerservice.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * Per-user token bucket rate limiter backed by Redis (Redisson).
 * Prevents a single user from overwhelming the LLM with rapid messages.
 * Rate is configurable via ai.chat.rate-limit-per-minute (default 10/min).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRateLimiter {

    private final RedissonClient redissonClient;
    private final ChatProperties properties;

    /**
     * Acquire one token for the given user. Throws if rate exceeded.
     *
     * @param username authenticated user
     * @throws RuntimeException if rate limit exceeded
     */
    public void acquire(String username) {
        String key = "chat:rate:" + username;
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        // trySetRate only sets if not already configured; idempotent
        limiter.trySetRate(
                RateType.OVERALL,
                properties.getRateLimitPerMinute(),
                1,
                RateIntervalUnit.MINUTES
        );
        if (!limiter.tryAcquire(1)) {
            log.warn("Rate limit exceeded for user: {}", username);
            throw new RuntimeException("提问过于频繁，请稍后再试");
        }
    }
}