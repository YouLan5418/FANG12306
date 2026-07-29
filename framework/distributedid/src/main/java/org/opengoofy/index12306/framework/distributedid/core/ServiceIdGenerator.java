package org.opengoofy.index12306.framework.distributedid.core;

import org.opengoofy.index12306.framework.distributedid.core.snowflake.SnowflakeIdInfo;

public interface ServiceIdGenerator extends IdGenerator {
    long nextId(long serviceId);
    default String nextIdStr(long serviceId) {
        return Long.toString(nextId(serviceId));
    }
    SnowflakeIdInfo parseSnowflakeId(long snowflakeId);
}
