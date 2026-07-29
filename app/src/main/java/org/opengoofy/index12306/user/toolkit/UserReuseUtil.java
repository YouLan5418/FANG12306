package org.opengoofy.index12306.user.toolkit;

/**
 * 可复用用户名 Redis 集合分片工具。
 */
public final class UserReuseUtil {

    /** 复用集合分片数 */
    public static final int REUSE_SHARDING_COUNT = 64;

    public static int hashShardingIdx(String username) {
        return Math.abs(username.hashCode() % REUSE_SHARDING_COUNT);
    }

    private UserReuseUtil() {
    }
}