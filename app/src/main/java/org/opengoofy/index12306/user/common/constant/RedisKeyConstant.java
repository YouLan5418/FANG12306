package org.opengoofy.index12306.user.common.constant;

/**
 * 用户域 Redis Key 常量。
 */
public final class RedisKeyConstant {

    /** 用户注册锁，前缀 + 用户名 */
    public static final String LOCK_USER_REGISTER = "index12306:user-service:lock:user-register:";

    /** 用户注销锁，前缀 + 用户名 */
    public static final String LOCK_USER_DELETION = "index12306:user-service:lock:user-deletion:";

    /** 注销后可复用用户名分片集合，前缀 + 分片下标 */
    public static final String USER_REGISTER_REUSE_SHARDING = "index12306:user-service:user-reuse:";

    /** 用户乘车人列表缓存，前缀 + 用户名 */
    public static final String USER_PASSENGER_LIST = "index12306:user-service:user-passenger-list:";

    private RedisKeyConstant() {
    }
}