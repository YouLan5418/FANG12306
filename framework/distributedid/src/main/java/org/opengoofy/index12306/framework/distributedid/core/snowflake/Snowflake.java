package org.opengoofy.index12306.framework.distributedid.core.snowflake;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import org.opengoofy.index12306.framework.distributedid.core.IdGenerator;

import java.io.Serializable;

/**
 * Twitter Snowflake 算法实现。
 * 结构: 符号位(1) - 时间戳(41) - 数据中心(5) - 机器(5) - 序列号(12)
 */
public class Snowflake implements Serializable, IdGenerator {

    private static final long serialVersionUID = 1L;

    private static final long DEFAULT_TWEPOCH = 1288834974657L;
    private static final long DEFAULT_TIME_OFFSET = 2000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long DATA_CENTER_ID_BITS = 5L;
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
    private static final long SEQUENCE_BITS = 12L;

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private final long twepoch;
    private final long workerId;
    private final long dataCenterId;
    private final boolean useSystemClock;
    private final long timeOffset;
    private final long randomSequenceLimit;

    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public Snowflake() {
        this(IdUtil.getWorkerId(IdUtil.getDataCenterId(MAX_DATA_CENTER_ID), MAX_WORKER_ID));
    }

    public Snowflake(long workerId) {
        this(workerId, IdUtil.getDataCenterId(MAX_DATA_CENTER_ID));
    }

    public Snowflake(long workerId, long dataCenterId) {
        this(workerId, dataCenterId, false, DEFAULT_TIME_OFFSET, DEFAULT_TWEPOCH);
    }

    public Snowflake(long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long twepoch) {
        Assert.isTrue(workerId >= 0 && workerId <= MAX_WORKER_ID,
                "workerId must be between [0, {}]", MAX_WORKER_ID);
        Assert.isTrue(dataCenterId >= 0 && dataCenterId <= MAX_DATA_CENTER_ID,
                "dataCenterId must be between [0, {}]", MAX_DATA_CENTER_ID);
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
        this.useSystemClock = isUseSystemClock;
        this.timeOffset = timeOffset;
        this.twepoch = twepoch;
        this.randomSequenceLimit = 0;
    }

    @Override
    public synchronized long nextId() {
        long timestamp = genTime();
        if (timestamp < lastTimestamp) {
            if (lastTimestamp - timestamp < timeOffset) {
                timestamp = lastTimestamp;
            } else {
                throw new IllegalStateException("Clock moved backwards. Refusing to generate id for "
                        + (lastTimestamp - timestamp) + " milliseconds");
            }
        }
        if (timestamp == lastTimestamp) {
            long tempSequence = (sequence + 1) & SEQUENCE_MASK;
            if (tempSequence == 0) {
                timestamp = tilNextSeconds(lastTimestamp);
            }
            sequence = tempSequence;
        } else {
            sequence = 0L;
            if (randomSequenceLimit > 1) {
                sequence = RandomUtil.randomLong(randomSequenceLimit);
            }
        }
        lastTimestamp = timestamp;
        return ((timestamp - twepoch) << TIMESTAMP_LEFT_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextSeconds(long lastTimestamp) {
        long timestamp = genTime();
        while (timestamp <= lastTimestamp) {
            timestamp = genTime();
        }
        return timestamp;
    }

    private long genTime() {
        return useSystemClock ? SystemClock.now() : System.currentTimeMillis();
    }
}
