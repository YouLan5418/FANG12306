package org.opengoofy.index12306.framework.cache.toolkit;

import cn.hutool.core.util.StrUtil;

public final class CacheUtil {

    private CacheUtil() {
    }

    public static boolean isNullOrBlank(Object obj) {
        return obj == null || (obj instanceof String && StrUtil.isBlank((String) obj));
    }
}
