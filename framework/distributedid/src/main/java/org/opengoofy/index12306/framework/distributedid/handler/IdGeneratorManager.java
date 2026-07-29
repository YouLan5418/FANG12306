package org.opengoofy.index12306.framework.distributedid.handler;

import lombok.NonNull;
import org.opengoofy.index12306.framework.distributedid.core.IdGenerator;
import org.opengoofy.index12306.framework.distributedid.core.ServiceIdGenerator;
import org.opengoofy.index12306.framework.distributedid.core.serviceid.DefaultServiceIdGenerator;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ID 生成器管理容器。
 * 按 resource key 注册并获取不同的 ID 生成器实例（如 order/pay 各自的生成器）。
 */
public final class IdGeneratorManager {

    private static final Map<String, IdGenerator> MANAGER = new ConcurrentHashMap<>();

    static {
        MANAGER.put("default", new DefaultServiceIdGenerator());
    }

    public static void registerIdGenerator(@NonNull String resource, @NonNull IdGenerator idGenerator) {
        MANAGER.putIfAbsent(resource, idGenerator);
    }

    public static ServiceIdGenerator getIdGenerator(@NonNull String resource) {
        return Optional.ofNullable(MANAGER.get(resource)).map(each -> (ServiceIdGenerator) each).orElse(null);
    }

    public static ServiceIdGenerator getDefaultServiceIdGenerator() {
        return Optional.ofNullable(MANAGER.get("default")).map(each -> (ServiceIdGenerator) each).orElse(null);
    }

    public static long nextId(String resource) {
        return getIdGenerator(resource).nextId();
    }

    public static String nextIdStr(String resource) {
        return getIdGenerator(resource).nextIdStr();
    }
}
