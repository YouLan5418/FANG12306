package org.opengoofy.index12306.order.service.orderid;

import org.opengoofy.index12306.framework.distributedid.toolkit.SnowflakeIdUtil;
import org.springframework.stereotype.Component;

/**
 * 订单号生成器（精简版）：基于雪花 ID。
 * 原项目用基因法把 userId 融入订单号以配合分库分表路由；本重构为 PG HASH(order_sn) 分区，
 * order_sn 直接作为分区键，无需基因法，雪花 ID 全局唯一即可。
 */
@Component
public final class OrderIdGeneratorManager {

    public static String generateOrderSn(Long userId) {
        return String.valueOf(SnowflakeIdUtil.nextId());
    }
}