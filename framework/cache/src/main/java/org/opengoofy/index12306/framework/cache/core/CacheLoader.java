package org.opengoofy.index12306.framework.cache.core;

@FunctionalInterface
public interface CacheLoader<T> {
    T load();
}
