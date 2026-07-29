package org.opengoofy.index12306.framework.cache;

import org.opengoofy.index12306.framework.cache.core.CacheLoader;
import org.redisson.api.RBloomFilter;

import java.util.concurrent.TimeUnit;

/**
 * 分布式缓存接口，扩展基础 Cache，支持回源加载与布隆过滤器防穿透。
 */
public interface DistributedCache extends Cache {

    <T> T get(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout);

    <T> T get(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit);

    <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout);

    <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit);

    <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, RBloomFilter<String> bloomFilter);

    <T> T safeGet(String key, Class<T> clazz, CacheLoader<T> cacheLoader, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter);

    void safePut(String key, Object value, long timeout, RBloomFilter<String> bloomFilter);

    void safePut(String key, Object value, long timeout, TimeUnit timeUnit, RBloomFilter<String> bloomFilter);

    Long countExistingKeys(String... keys);
}
