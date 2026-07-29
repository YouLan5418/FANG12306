package org.opengoofy.index12306.framework.cache;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.opengoofy.index12306.framework.cache.core.CacheLoader;
import org.opengoofy.index12306.framework.cache.toolkit.CacheUtil;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 分布式缓存操作 Redis 模板代理（精简版）。
 * 提供 get/put/safeGet/delete 等能力，safeGet 通过分布式锁+双重判定+布隆过滤器防止缓存穿透。
 */
@RequiredArgsConstructor
public class StringRedisTemplateProxy implements DistributedCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;

    private static final String SAFE_GET_DISTRIBUTED_LOCK_KEY_PREFIX = "safe_get_distributed_lock:";

    @Override
    public <T> T get(String key, Class<T> clazz) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(clazz)) {
            return (T) value;
        }
        return JSON.parseObject(value, clazz);
    }

    @Override
    public void put(String key, Object value) {
        put(key, value, 30, TimeUnit.DAYS);
    }

    @Override
    public void put(String key, Object value, long timeout) {
        put(key, value, timeout, TimeUnit.SECONDS);
    }

    @Override
    public void put(String key, Object value, long timeout, TimeUnit timeUnit) {
        String actual = value instanceof String ? (String) value : JSON.toJSONString(value);
        stringRedisTemplate.opsForValue().set(key, actual, timeout, timeUnit);
    }

    @Override
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    @Override
    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    @Override
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    @Override
    public Object getInstance() {
        return stringRedisTemplate;
    }

    @Override
    public Long countExistingKeys(String... keys) {
        return stringRedisTemplate.countExistingKeys(Lists.newArrayList(keys));
    }

    @Override
    public <T> T get(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout) {
        return get(key, clazz, cacheLoader, timeout, TimeUnit.SECONDS);
    }

    @Override
    public <T> T get(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit) {
        T result = get(key, clazz);
        if (!CacheUtil.isNullOrBlank(result)) {
            return result;
        }
        return loadAndSet(key, cacheLoader, timeout, timeUnit, false, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout) {
        return safeGet(key, clazz, cacheLoader, timeout, TimeUnit.SECONDS, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit) {
        return safeGet(key, clazz, cacheLoader, timeout, timeUnit, null);
    }

    @Override
    public <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, RBloomFilter<String> bloomFilter) {
        return safeGet(key, clazz, cacheLoader, timeout, TimeUnit.SECONDS, bloomFilter);
    }

    @Override
    public <T> T safeGet(@NotBlank String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter) {
        T result = get(key, clazz);
        if (!CacheUtil.isNullOrBlank(result)
                || Optional.ofNullable(bloomFilter).map(each -> !each.contains(key)).orElse(false)) {
            return result;
        }
        RLock lock = redissonClient.getLock(SAFE_GET_DISTRIBUTED_LOCK_KEY_PREFIX + key);
        lock.lock();
        try {
            if (CacheUtil.isNullOrBlank(result = get(key, clazz))) {
                if (CacheUtil.isNullOrBlank(result = loadAndSet(key, cacheLoader, timeout, timeUnit, true, bloomFilter))) {
                    return null;
                }
            }
        } finally {
            lock.unlock();
        }
        return result;
    }

    @Override
    public void safePut(String key, Object value, long timeout, RBloomFilter<String> bloomFilter) {
        safePut(key, value, timeout, TimeUnit.SECONDS, bloomFilter);
    }

    @Override
    public void safePut(String key, Object value, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter) {
        put(key, value, timeout, timeUnit);
        if (bloomFilter != null) {
            bloomFilter.add(key);
        }
    }

    private <T> T loadAndSet(String key, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, boolean safeFlag, RBloomFilter<String> bloomFilter) {
        T result = cacheLoader.load();
        if (CacheUtil.isNullOrBlank(result)) {
            return result;
        }
        if (safeFlag) {
            safePut(key, result, timeout, timeUnit, bloomFilter);
        } else {
            put(key, result, timeout, timeUnit);
        }
        return result;
    }
}
