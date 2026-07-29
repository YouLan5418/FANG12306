package org.opengoofy.index12306.framework.distributedid.core.serviceid;

import org.opengoofy.index12306.framework.distributedid.core.IdGenerator;
import org.opengoofy.index12306.framework.distributedid.core.ServiceIdGenerator;
import org.opengoofy.index12306.framework.distributedid.core.snowflake.Snowflake;
import org.opengoofy.index12306.framework.distributedid.core.snowflake.SnowflakeIdInfo;

/**
 * 默认业务 ID 生成器。
 * 在雪花 ID 基础上混入 serviceId 的低 4 位作为基因，便于按业务分片。
 */
public final class DefaultServiceIdGenerator implements ServiceIdGenerator {

    private final IdGenerator idGenerator;
    private final long maxBizIdBitsLen;

    public DefaultServiceIdGenerator() {
        this(SEQUENCE_BIZ_BITS);
    }

    public DefaultServiceIdGenerator(long serviceIdBitLen) {
        idGenerator = new Snowflake();
        this.maxBizIdBitsLen = (long) Math.pow(2, serviceIdBitLen);
    }

    @Override
    public long nextId(long serviceId) {
        long id = Math.abs(Long.valueOf(serviceId).hashCode()) % this.maxBizIdBitsLen;
        long nextId = idGenerator.nextId();
        return nextId | id;
    }

    @Override
    public long nextId() {
        return idGenerator.nextId();
    }

    @Override
    public SnowflakeIdInfo parseSnowflakeId(long snowflakeId) {
        return SnowflakeIdInfo.builder()
                .workerId((snowflakeId >> WORKER_ID_SHIFT) & ~(-1L << WORKER_ID_BITS))
                .dataCenterId((snowflakeId >> DATA_CENTER_ID_SHIFT) & ~(-1L << DATA_CENTER_ID_BITS))
                .timestamp((snowflakeId >> TIMESTAMP_LEFT_SHIFT) + DEFAULT_TWEPOCH)
                .sequence((snowflakeId >> SEQUENCE_BIZ_BITS) & ~(-1L << SEQUENCE_ACTUAL_BITS))
                .gene(snowflakeId & ~(-1L << SEQUENCE_BIZ_BITS))
                .build();
    }

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATA_CENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long SEQUENCE_ACTUAL_BITS = 8L;
    private static final long SEQUENCE_BIZ_BITS = 4L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DEFAULT_TWEPOCH = 1288834974657L;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
}
