package org.opengoofy.index12306.framework.distributedid.toolkit;

import cn.hutool.core.util.IdUtil;
import org.opengoofy.index12306.framework.distributedid.core.IdGenerator;
import org.opengoofy.index12306.framework.distributedid.core.snowflake.Snowflake;

/**
 * 雪花 ID 工具类，单例持有 Snowflake 实例。
 */
public final class SnowflakeIdUtil {

    private static final IdGenerator ID_GENERATOR = new Snowflake(IdUtil.getWorkerId(IdUtil.getDataCenterId(31L), 31L));

    public static long nextId() {
        return ID_GENERATOR.nextId();
    }

    public static String nextIdStr() {
        return ID_GENERATOR.nextIdStr();
    }

    public static IdGenerator getInstance() {
        return ID_GENERATOR;
    }
}
