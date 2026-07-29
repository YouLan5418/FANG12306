package org.opengoofy.index12306.framework.distributedid.core;

public interface IdGenerator {
    long nextId();
    default String nextIdStr() {
        return Long.toString(nextId());
    }
}
