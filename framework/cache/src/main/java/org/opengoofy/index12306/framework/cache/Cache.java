package org.opengoofy.index12306.framework.cache;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * 分布式缓存抽象接口。
 */
public interface Cache {

    <T> T get(String key, Class<T> clazz);

    void put(String key, Object value);

    void put(String key, Object value, long timeout);

    void put(String key, Object value, long timeout, TimeUnit timeUnit);

    Boolean delete(String key);

    Long delete(Collection<String> keys);

    Boolean hasKey(String key);

    Object getInstance();
}
