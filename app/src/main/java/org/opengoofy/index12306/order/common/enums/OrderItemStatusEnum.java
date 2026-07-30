package org.opengoofy.index12306.order.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单明细状态：0待支付 10已支付 20已进站 30已取消 40已退票 50已改签
 */
@Getter
@AllArgsConstructor
public enum OrderItemStatusEnum {

    PENDING_PAYMENT(0),
    ALREADY_PAID(10),
    CHECKED_IN(20),
    CLOSED(30),
    REFUNDED(40),
    CHANGED(50);

    private final int status;
}