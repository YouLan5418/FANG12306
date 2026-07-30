package org.opengoofy.index12306.pay.service.payid;

import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.springframework.stereotype.Component;

/**
 * 支付单号生成器（雪花）。
 */
@Component
public final class PayIdGeneratorManager {

    public static String generatePaySn(String orderSn) {
        return String.valueOf(SnowflakeIdUtil.nextId());
    }
}